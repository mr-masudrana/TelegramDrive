package com.telegramdrive.app.repository

import android.util.Base64
import com.telegramdrive.app.data.DriveFileDao
import com.telegramdrive.app.data.DriveFileEntity
import com.telegramdrive.app.data.DriveFolderDao
import com.telegramdrive.app.data.DriveFolderEntity
import com.telegramdrive.app.telegram.TelegramClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import java.io.File

private const val FOLDER_TAG = "#folderdef:"
const val TRASH_PATH = "/.Trash"

/**
 * Single entry point the ViewModels talk to. Keeps TDLib details out of the UI layer
 * and keeps the local Room index in sync with what's actually in the channel.
 *
 * Folders aren't a real Telegram concept — a folder "exists" because a small text
 * message tagged #folderdef:<path> was sent to the channel. Files carry their own
 * folder in a #folder:<path> caption tag (see buildCaption). Both are re-derived
 * from the channel's message history on every refresh, so the channel stays the
 * single source of truth and local Room tables are just a cache.
 *
 * "Deleting" a file actually moves it into [TRASH_PATH] (re-tagging the caption) —
 * the message isn't really removed from Telegram until [permanentlyDeleteFile] is
 * called, which lets the user restore from Trash.
 */
class DriveRepository(
    private val telegram: TelegramClient,
    private val fileDao: DriveFileDao,
    private val folderDao: DriveFolderDao
) {

    fun observeFolder(folderPath: String): Flow<List<DriveFileEntity>> =
        fileDao.observeFolder(folderPath)

    fun observeSubFolders(parentPath: String): Flow<List<String>> =
        folderDao.observeChildren(parentPath).map { list -> list.map { it.name } }

    suspend fun uploadFile(localFile: File, folderPath: String): Int {
        val chatId = telegram.storageChatId.value ?: error("Storage channel not ready yet")
        return telegram.uploadFile(chatId, localFile.absolutePath, buildCaption(folderPath, null, null))
    }

    suspend fun createFolder(parentPath: String, name: String) {
        val chatId = telegram.storageChatId.value ?: error("Storage channel not ready yet")
        val childPath = joinPath(parentPath, name)
        telegram.sendTextMessage(chatId, "$FOLDER_TAG$childPath")
        refreshFolder(parentPath)
    }

    /** Refreshes both the files AND the sub-folders visible directly inside [folderPath]. */
    suspend fun refreshFolder(folderPath: String) {
        val chatId = telegram.storageChatId.value ?: return
        val messages = telegram.listMessages(chatId)

        val fileEntities = messages.mapNotNull { it.toDriveFileEntityOrNull(folderPath) }
        fileDao.replaceFolder(folderPath, fileEntities)

        val folderEntities = messages.mapNotNull { it.toDriveFolderEntityOrNull(folderPath) }
            .distinctBy { it.path }
        folderDao.replaceChildren(folderPath, folderEntities)
    }

    /** Downloads the file to TDLib's own storage and returns it, ready to open with a FileProvider Uri. */
    suspend fun downloadFile(entity: DriveFileEntity): File {
        val path = telegram.downloadFileSync(entity.telegramFileId)
        return File(path)
    }

    /** Soft-delete: moves the message into the Trash folder instead of actually removing it. */
    suspend fun deleteFile(entity: DriveFileEntity) {
        val caption = buildCaption(TRASH_PATH, customNameOrNull(entity), entity.folderPath)
        telegram.editCaption(entity.chatId, entity.messageId, caption)
        fileDao.delete(entity.messageId) // optimistic — vanishes from current folder right away
    }

    suspend fun restoreFile(entity: DriveFileEntity) {
        val restoreTo = entity.trashedFromPath ?: "/"
        telegram.editCaption(entity.chatId, entity.messageId, buildCaption(restoreTo, customNameOrNull(entity), null))
        fileDao.delete(entity.messageId) // vanishes from the Trash view right away
    }

    /** Really deletes the message from Telegram — no going back. */
    suspend fun permanentlyDeleteFile(entity: DriveFileEntity) {
        telegram.deleteMessage(entity.chatId, entity.messageId)
        fileDao.delete(entity.messageId)
    }

    suspend fun emptyTrash() {
        val chatId = telegram.storageChatId.value ?: return
        val messages = telegram.listMessages(chatId)
        val trashed = messages.mapNotNull { it.toDriveFileEntityOrNull(TRASH_PATH) }
        trashed.forEach { telegram.deleteMessage(it.chatId, it.messageId) }
        fileDao.clearFolder(TRASH_PATH)
    }

    private fun customNameOrNull(entity: DriveFileEntity): String? =
        entity.fileName.takeIf { true } // name is always carried forward as-is; see buildCaption

    suspend fun renameFile(entity: DriveFileEntity, newName: String) {
        telegram.editCaption(entity.chatId, entity.messageId, buildCaption(entity.folderPath, newName, entity.trashedFromPath))
        fileDao.upsert(entity.copy(fileName = newName)) // optimistic UI update
    }

    /** Sends a fresh message pointing at the same remote file — no re-upload. */
    suspend fun copyFile(entity: DriveFileEntity, destFolderPath: String) {
        telegram.copyDocument(entity.chatId, entity.remoteFileId, buildCaption(destFolderPath, null, null))
        refreshFolder(destFolderPath)
    }

    /** A "move" is just re-tagging which folder the same message belongs to. */
    suspend fun moveFile(entity: DriveFileEntity, destFolderPath: String) {
        telegram.editCaption(entity.chatId, entity.messageId, buildCaption(destFolderPath, null, null))
        fileDao.delete(entity.messageId) // optimistic — vanishes from the old folder's list right away
        refreshFolder(destFolderPath)
    }

    suspend fun getShareLink(entity: DriveFileEntity): String =
        telegram.getMessageLink(entity.chatId, entity.messageId)

    /** Sums the size of every file across the whole channel (all folders), paging through full history. */
    suspend fun getTotalStorageBytes(): Long {
        val chatId = telegram.storageChatId.value ?: return 0L
        var total = 0L
        var fromId = 0L
        while (true) {
            val page = telegram.listMessages(chatId, fromMessageId = fromId, limit = 100)
            if (page.isEmpty()) break
            for (m in page) {
                val doc = (m.content as? TdApi.MessageDocument)?.document
                if (doc != null) total += doc.document.size.toLong()
            }
            fromId = page.last().id
            if (page.size < 100) break
        }
        return total
    }

    suspend fun getMe() = telegram.getMe()

    suspend fun logOut() {
        telegram.logOut()
        fileDao.clearAll()
        folderDao.clearAll()
    }

    /** Fetches every file across the whole channel (all folders) — used for global search. */
    suspend fun getAllFiles(): List<DriveFileEntity> {
        val chatId = telegram.storageChatId.value ?: return emptyList()
        val all = mutableListOf<DriveFileEntity>()
        var fromId = 0L
        while (true) {
            val page = telegram.listMessages(chatId, fromMessageId = fromId, limit = 100)
            if (page.isEmpty()) break
            all += page.mapNotNull { it.toDriveFileEntity() }
            fromId = page.last().id
            if (page.size < 100) break
        }
        return all
    }

    // ---- Path helpers ----

    fun joinPath(parent: String, name: String): String =
        if (parent == "/") "/$name" else "$parent/$name"

    fun parentOf(path: String): String {
        val trimmed = path.trimEnd('/')
        val idx = trimmed.lastIndexOf('/')
        return if (idx <= 0) "/" else trimmed.substring(0, idx)
    }

    // ---- Parsing message history into files / folders ----

    private fun buildCaption(folderPath: String, customName: String?, trashedFrom: String?): String {
        val lines = mutableListOf("#folder:$folderPath")
        if (customName != null) lines += "#name:$customName"
        if (trashedFrom != null) lines += "#trashedFrom:$trashedFrom"
        return lines.joinToString("\n")
    }

    private fun TdApi.Message.toDriveFileEntityOrNull(expectedFolder: String): DriveFileEntity? {
        val entity = toDriveFileEntity() ?: return null
        return entity.takeIf { it.folderPath == expectedFolder }
    }

    private fun TdApi.Message.toDriveFileEntity(): DriveFileEntity? {
        val doc = (content as? TdApi.MessageDocument)?.document ?: return null
        val captionLines = (content as TdApi.MessageDocument).caption?.text.orEmpty().lines()
        val folder = captionLines.firstOrNull { it.startsWith("#folder:") }
            ?.removePrefix("#folder:") ?: "/"
        val customName = captionLines.firstOrNull { it.startsWith("#name:") }?.removePrefix("#name:")
        val trashedFrom = captionLines.firstOrNull { it.startsWith("#trashedFrom:") }?.removePrefix("#trashedFrom:")
        val thumbnailBase64 = runCatching {
            doc.document.minithumbnail?.data?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        }.getOrNull()
        return DriveFileEntity(
            messageId = id,
            chatId = chatId,
            fileName = customName ?: doc.fileName,
            folderPath = folder,
            sizeBytes = doc.document.size.toLong(),
            mimeType = doc.mimeType,
            telegramFileId = doc.document.id,
            remoteFileId = doc.document.remote.id,
            thumbnailBase64 = thumbnailBase64,
            trashedFromPath = trashedFrom,
            uploadedAtEpochSec = date.toLong()
        )
    }

    /** Recognizes a #folderdef:<path> marker message and returns it only if it's a DIRECT child of [expectedParent]. */
    private fun TdApi.Message.toDriveFolderEntityOrNull(expectedParent: String): DriveFolderEntity? {
        val text = (content as? TdApi.MessageText)?.text?.text ?: return null
        if (!text.startsWith(FOLDER_TAG)) return null
        val path = text.removePrefix(FOLDER_TAG).trim()
        if (parentOf(path) != expectedParent) return null
        val name = path.trimEnd('/').substringAfterLast('/')
        return DriveFolderEntity(
            messageId = id,
            chatId = chatId,
            path = path,
            parentPath = expectedParent,
            name = name
        )
    }
}
