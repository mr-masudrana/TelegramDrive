package com.telegramdrive.app.telegram

import android.content.Context
import com.telegramdrive.app.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
// These two classes come from TDLib's own Java/JNI bindings — see README "Option A".
// They are NOT part of this skeleton and must be added from the TDLib build output.
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

private const val TAG = "TelegramClient"

data class FileProgress(
    val fileId: Int,
    val progress: Float,       // 0f..1f
    val isDownload: Boolean,
    val completed: Boolean,
    val localPath: String?
)

/**
 * Wraps TDLib's callback-based [Client] in Kotlin coroutines so the rest of the app
 * can call suspend functions instead of dealing with TdApi.ResultHandler directly.
 *
 * One instance is shared for the whole app (see TelegramDriveApp).
 */
class TelegramClient(private val appContext: Context, private val accountId: String) {

    // local.properties (local build) বা GitHub Actions Secrets (CI build) থেকে আসে —
    // দেখুন app/build.gradle.kts এর tgApiId/tgApiHash অংশ।
    private val apiId = BuildConfig.TG_API_ID
    private val apiHash = BuildConfig.TG_API_HASH

    private val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        _authState.value = AuthState.Error(throwable.message ?: "Unknown error")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** chatId of the user's private "storage" channel, once known. */
    private val _storageChatId = MutableStateFlow<Long?>(null)
    val storageChatId: StateFlow<Long?> = _storageChatId.asStateFlow()

    /** fileId → live upload/download progress, updated from TDLib's UpdateFile events. */
    private val _fileProgress = MutableStateFlow<Map<Int, FileProgress>>(emptyMap())
    val fileProgress: StateFlow<Map<Int, FileProgress>> = _fileProgress.asStateFlow()

    private val client: Client = Client.create(
        /* updatesHandler = */ this::handleUpdate,
        /* updateExceptionHandler = */ null,
        /* defaultExceptionHandler = */ null
    )

    init {
        setLogVerbosity()
    }

    private fun setLogVerbosity() {
        Client.execute(TdApi.SetLogVerbosityLevel(1))
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> onAuthorizationState(update.authorizationState)
            is TdApi.UpdateFile -> onFileUpdate(update.file)
        }
    }

    private fun onFileUpdate(file: TdApi.File) {
        val total = if (file.size > 0) file.size else file.expectedSize
        val isUploading = file.remote.isUploadingActive || file.remote.isUploadingCompleted
        val downloadedOrUploaded = if (isUploading) file.remote.uploadedSize else file.local.downloadedSize
        val fraction = if (total > 0) (downloadedOrUploaded.toFloat() / total).coerceIn(0f, 1f) else 0f
        val completed = if (isUploading) file.remote.isUploadingCompleted else file.local.isDownloadingCompleted

        _fileProgress.value = _fileProgress.value + (file.id to FileProgress(
            fileId = file.id,
            progress = fraction,
            isDownload = !isUploading,
            completed = completed,
            localPath = file.local.path.takeIf { it.isNotEmpty() }
        ))
    }

    private fun onAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                client.send(buildParameters(), {})
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = AuthState.WaitPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> _authState.value = AuthState.WaitCode
            is TdApi.AuthorizationStateWaitPassword -> _authState.value = AuthState.WaitPassword
            is TdApi.AuthorizationStateReady -> {
                _authState.value = AuthState.Ready
                scope.launch { findOrCreateStorageChannel() }
            }
            is TdApi.AuthorizationStateClosed -> { /* client fully stopped */ }
            else -> { /* logging out, closing, etc. */ }
        }
    }

    private fun buildParameters(): TdApi.SetTdlibParameters {
        val dir = appContext.filesDir.absolutePath
        return TdApi.SetTdlibParameters().apply {
            databaseDirectory = "$dir/tdlib_$accountId"
            filesDirectory = "$dir/tdlib_files_$accountId"
            useMessageDatabase = true
            useSecretChats = false
            apiId = this@TelegramClient.apiId
            apiHash = this@TelegramClient.apiHash
            systemLanguageCode = "en"
            deviceModel = "Android"
            applicationVersion = "0.1.0"
        }
    }

    // ---- Generic suspend bridge over TDLib's callback API ----

    private suspend fun <R : TdApi.Object> send(function: TdApi.Function<R>): R =
        withContext(Dispatchers.IO) {
            val result = CompletableDeferred<R>()
            client.send(function) { obj ->
                if (obj is TdApi.Error) {
                    result.completeExceptionally(RuntimeException("${obj.code}: ${obj.message}"))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    result.complete(obj as R)
                }
            }
            result.await()
        }

    // ---- Auth flow, called from the login UI ----
    // Errors are caught here and surfaced via authState instead of crashing the app,
    // so the UI can show what actually went wrong (e.g. wrong api_id/api_hash).

    suspend fun submitPhoneNumber(phone: String) {
        runCatching { send(TdApi.SetAuthenticationPhoneNumber(phone, null)) }
            .onFailure { _authState.value = AuthState.Error(it.message ?: "Unknown error") }
    }

    suspend fun submitCode(code: String) {
        runCatching { send(TdApi.CheckAuthenticationCode(code)) }
            .onFailure { _authState.value = AuthState.Error(it.message ?: "Unknown error") }
    }

    suspend fun submitPassword(password: String) {
        runCatching { send(TdApi.CheckAuthenticationPassword(password)) }
            .onFailure { _authState.value = AuthState.Error(it.message ?: "Unknown error") }
    }

    // ---- Storage channel management ----

    private suspend fun findOrCreateStorageChannel() {
        // In a real app: first check Room/DataStore for a cached chatId, and verify with
        // TdApi.GetChat before falling back to search/create. Simplified here.
        val existing = findExistingStorageChannel()
        val chatId = existing ?: createStorageChannel()
        _storageChatId.value = chatId
    }

    private suspend fun findExistingStorageChannel(): Long? {
        val chats = send(TdApi.GetChats().apply { limit = 100 })
        for (id in chats.chatIds) {
            val chat = send(TdApi.GetChat(id))
            if (chat.title == STORAGE_CHANNEL_TITLE) return id
        }
        return null
    }

    private suspend fun createStorageChannel(): Long {
        val request = TdApi.CreateNewSupergroupChat().apply {
            title = STORAGE_CHANNEL_TITLE
            isChannel = true
            isForum = false
            description = "App storage created by TelegramDrive"
        }
        val chat = send(request)
        return chat.id
    }

    // ---- Files ----

    /** Sends a local file to the storage channel; [captionMeta] can encode a virtual folder path.
     *  Returns the TDLib fileId immediately (upload continues in the background — track via [fileProgress]). */
    suspend fun uploadFile(chatId: Long, localPath: String, captionMeta: String): Int {
        val inputDoc = TdApi.InputDocument().apply {
            document = TdApi.InputFileLocal().apply { path = localPath }
        }
        val content = TdApi.InputMessageDocument().apply {
            document = inputDoc
            caption = TdApi.FormattedText().apply {
                text = captionMeta
                entities = emptyArray()
            }
        }
        val request = TdApi.SendMessage().apply {
            this.chatId = chatId
            inputMessageContent = content
        }
        val message = send(request)
        val doc = (message.content as TdApi.MessageDocument).document
        return doc.document.id
    }

    suspend fun getMe(): TdApi.User = send(TdApi.GetMe())

    /** Logs this account out. TDLib keeps the same Client and returns to WaitPhoneNumber. */
    suspend fun logOut() {
        send(TdApi.LogOut())
    }

    /** Sends a plain text message — used as a "folder marker" (tagged #folderdef:<path>). */
    suspend fun sendTextMessage(chatId: Long, text: String) {
        val content = TdApi.InputMessageText().apply {
            this.text = TdApi.FormattedText().apply {
                this.text = text
                entities = emptyArray()
            }
        }
        val request = TdApi.SendMessage().apply {
            this.chatId = chatId
            inputMessageContent = content
        }
        send(request)
    }

    suspend fun listMessages(chatId: Long, fromMessageId: Long = 0, limit: Int = 50): List<TdApi.Message> {
        val request = TdApi.GetChatHistory().apply {
            this.chatId = chatId
            this.fromMessageId = fromMessageId
            this.limit = limit
        }
        val history = send(request)
        return history.messages.toList()
    }

    fun downloadFile(fileId: Int) {
        val request = TdApi.DownloadFile().apply {
            this.fileId = fileId
            priority = 1
        }
        client.send(request) {}
    }

    /**
     * Downloads the file synchronously (suspends until it's fully on disk, or throws)
     * and returns its local path — ready to open with an Intent + FileProvider.
     */
    suspend fun downloadFileSync(fileId: Int): String {
        val request = TdApi.DownloadFile().apply {
            this.fileId = fileId
            priority = 1
            synchronous = true
        }
        val file = send(request)
        return file.local.path
    }

    /** Re-sends an already-uploaded file by its remote id — no re-upload needed. Used for "copy". */
    suspend fun copyDocument(chatId: Long, remoteFileId: String, captionMeta: String): Int {
        val inputDoc = TdApi.InputDocument().apply {
            document = TdApi.InputFileRemote().apply { id = remoteFileId }
        }
        val content = TdApi.InputMessageDocument().apply {
            document = inputDoc
            caption = TdApi.FormattedText().apply {
                text = captionMeta
                entities = emptyArray()
            }
        }
        val request = TdApi.SendMessage().apply {
            this.chatId = chatId
            inputMessageContent = content
        }
        val message = send(request)
        val doc = (message.content as TdApi.MessageDocument).document
        return doc.document.id
    }

    /** A t.me link to the message — only openable by members of the (private) storage channel. */
    suspend fun getMessageLink(chatId: Long, messageId: Long): String {
        val request = TdApi.GetMessageLink().apply {
            this.chatId = chatId
            this.messageId = messageId
        }
        return send(request).link
    }

    suspend fun deleteMessage(chatId: Long, messageId: Long) {
        val request = TdApi.DeleteMessages().apply {
            this.chatId = chatId
            messageIds = longArrayOf(messageId)
            revoke = true
        }
        send(request)
    }

    /** Re-sends the message caption with an updated `#name:` tag to rename a file. */
    suspend fun editCaption(chatId: Long, messageId: Long, newCaptionText: String) {
        val request = TdApi.EditMessageCaption().apply {
            this.chatId = chatId
            this.messageId = messageId
            caption = TdApi.FormattedText().apply {
                text = newCaptionText
                entities = emptyArray()
            }
        }
        send(request)
    }

    companion object {
        private const val STORAGE_CHANNEL_TITLE = "TelegramDrive Storage"
    }
}
