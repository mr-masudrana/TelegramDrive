package com.telegramdrive.app.backup

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.telegramdrive.app.TelegramDriveApp
import com.telegramdrive.app.data.AppDatabase
import com.telegramdrive.app.data.BackedUpMediaEntity
import com.telegramdrive.app.repository.DriveRepository
import com.telegramdrive.app.telegram.AuthState
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Runs periodically in the background (see [BackupScheduler]). Finds DCIM photos/videos
 * that haven't been uploaded yet and sends them to the [BACKUP_FOLDER_PATH] folder.
 */
class AutoBackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!AutoBackupPrefs(applicationContext).isEnabled) return Result.success()

        val app = applicationContext as TelegramDriveApp
        val telegram = app.telegramClient

        // TDLib এখনো লগইন/চ্যানেল-রেডি না হলে এই রানটা স্কিপ করে পরের বার আবার চেষ্টা করবে।
        if (telegram.authState.first() !is AuthState.Ready) return Result.retry()
        if (telegram.storageChatId.first() == null) return Result.retry()

        val db = AppDatabase.get(applicationContext)
        val repository = DriveRepository(telegram, db.driveFileDao(), db.driveFolderDao())
        val backedUpDao = db.backedUpMediaDao()

        val candidates = queryDcimMedia()
        val alreadyDone = backedUpDao.filterAlreadyBackedUp(candidates.map { it.id }).toSet()
        val pending = candidates.filter { it.id !in alreadyDone }

        for (item in pending) {
            runCatching {
                val localCopy = copyToCache(item.uri, item.displayName)
                repository.uploadFile(localCopy, BACKUP_FOLDER_PATH)
                localCopy.delete() // TDLib কপি করে নেয় নিজের স্টোরেজে, তাই ক্যাশ কপি রাখার দরকার নেই
                backedUpDao.markBackedUp(BackedUpMediaEntity(item.id, System.currentTimeMillis() / 1000))
            }
            // একটা ফাইল ব্যর্থ হলেও বাকিগুলোর চেষ্টা চালিয়ে যায়; ব্যর্থ ফাইলটা পরের রানে আবার চেষ্টা হবে
            // (backedUpDao-তে মার্ক করা হয়নি বলে)।
        }

        if (pending.isNotEmpty()) repository.refreshFolder(BACKUP_FOLDER_PATH)
        return Result.success()
    }

    private data class MediaItem(val id: Long, val uri: Uri, val displayName: String)

    private fun queryDcimMedia(): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)

        // RELATIVE_PATH শুধু Android 10 (API 29)+ এ আছে; তার আগের ভার্সনে DATA (পুরনো ফাইল-পাথ কলাম) ব্যবহার করি।
        val useRelativePath = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
        val selection = if (useRelativePath) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ?"
        }
        val selectionArgs = if (useRelativePath) arrayOf("DCIM/%") else arrayOf("%/DCIM/%")

        for (collection in collections) {
            applicationContext.contentResolver.query(
                collection, projection, selection, selectionArgs, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "media_$id"
                    result += MediaItem(id, Uri.withAppendedPath(collection, id.toString()), name)
                }
            }
        }
        return result
    }

    private fun copyToCache(uri: Uri, displayName: String): File {
        val target = File(applicationContext.cacheDir, "backup_$displayName")
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }
}
