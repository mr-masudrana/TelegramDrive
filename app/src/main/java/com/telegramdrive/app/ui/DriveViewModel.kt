package com.telegramdrive.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramdrive.app.data.DriveFileEntity
import com.telegramdrive.app.repository.DriveRepository
import com.telegramdrive.app.repository.TRASH_PATH
import com.telegramdrive.app.telegram.AuthState
import com.telegramdrive.app.telegram.TelegramClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class SortOption(val label: String) {
    DATE_NEWEST("সর্বশেষ প্রথমে"),
    DATE_OLDEST("পুরোনো প্রথমে"),
    NAME_ASC("নাম (A-Z)"),
    NAME_DESC("নাম (Z-A)"),
    SIZE_LARGEST("বড় সাইজ প্রথমে"),
    SIZE_SMALLEST("ছোট সাইজ প্রথমে")
}

enum class FileTypeFilter(val label: String) {
    ALL("সব"),
    IMAGES("ছবি"),
    VIDEOS("ভিডিও"),
    DOCUMENTS("ডকুমেন্ট"),
    OTHERS("অন্যান্য")
}

class DriveViewModel(
    private val telegram: TelegramClient,
    private val repository: DriveRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = telegram.authState

    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_NEWEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _typeFilter = MutableStateFlow(FileTypeFilter.ALL)
    val typeFilter: StateFlow<FileTypeFilter> = _typeFilter.asStateFlow()

    private val rawFilesInCurrentFolder = currentPath.flatMapLatest { repository.observeFolder(it) }

    val filesInCurrentFolder: StateFlow<List<DriveFileEntity>> =
        combine(rawFilesInCurrentFolder, _sortOption, _typeFilter) { files, sort, filter ->
            applyFilterSort(files, filter, sort)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subFoldersInCurrentFolder: StateFlow<List<String>> =
        currentPath.flatMapLatest { repository.observeSubFolders(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortOption(option: SortOption) { _sortOption.value = option }
    fun setTypeFilter(filter: FileTypeFilter) { _typeFilter.value = filter }

    private fun applyFilterSort(files: List<DriveFileEntity>, filter: FileTypeFilter, sort: SortOption): List<DriveFileEntity> {
        val filtered = files.filter { matchesTypeFilter(it, filter) }
        return when (sort) {
            SortOption.DATE_NEWEST -> filtered.sortedByDescending { it.uploadedAtEpochSec }
            SortOption.DATE_OLDEST -> filtered.sortedBy { it.uploadedAtEpochSec }
            SortOption.NAME_ASC -> filtered.sortedBy { it.fileName.lowercase() }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.fileName.lowercase() }
            SortOption.SIZE_LARGEST -> filtered.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_SMALLEST -> filtered.sortedBy { it.sizeBytes }
        }
    }

    private fun matchesTypeFilter(file: DriveFileEntity, filter: FileTypeFilter): Boolean {
        val mime = file.mimeType.orEmpty()
        return when (filter) {
            FileTypeFilter.ALL -> true
            FileTypeFilter.IMAGES -> mime.startsWith("image/")
            FileTypeFilter.VIDEOS -> mime.startsWith("video/")
            FileTypeFilter.DOCUMENTS -> mime == "application/pdf" || mime.contains("document") ||
                mime.contains("text") || mime.contains("word") || mime.contains("sheet") || mime.contains("presentation")
            FileTypeFilter.OTHERS -> !matchesTypeFilter(file, FileTypeFilter.IMAGES) &&
                !matchesTypeFilter(file, FileTypeFilter.VIDEOS) && !matchesTypeFilter(file, FileTypeFilter.DOCUMENTS)
        }
    }

    // ---- Global search (fetches the whole channel once, then filters in memory) ----

    private var allFilesCache: List<DriveFileEntity>? = null

    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<DriveFileEntity>>(emptyList())
    val searchResults: StateFlow<List<DriveFileEntity>> = _searchResults.asStateFlow()

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    fun openSearch() {
        _searchActive.value = true
        if (allFilesCache == null) {
            viewModelScope.launch {
                _isSearchLoading.value = true
                runCatching { repository.getAllFiles() }
                    .onSuccess { allFilesCache = it }
                    .onFailure { _errorMessages.emit(it.message ?: "Search index failed") }
                _isSearchLoading.value = false
            }
        }
    }

    fun closeSearch() {
        _searchActive.value = false
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        val cache = allFilesCache
        _searchResults.value = if (query.isBlank() || cache == null) {
            emptyList()
        } else {
            cache.filter { it.fileName.contains(query, ignoreCase = true) }
        }
    }

    /** file name → 0f..1f, only contains files currently uploading. */
    private val _uploadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val uploadProgress: StateFlow<Map<String, Float>> = _uploadProgress.asStateFlow()

    /** messageId → 0f..1f, only contains files currently downloading. */
    private val _downloadProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<Long, Float>> = _downloadProgress.asStateFlow()

    /** Multi-select state for batch download/delete. */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _fileReadyToOpen = MutableSharedFlow<Pair<DriveFileEntity, File>>(extraBufferCapacity = 1)
    val fileReadyToOpen: SharedFlow<Pair<DriveFileEntity, File>> = _fileReadyToOpen

    /** Downloaded (not just to open, but to hand off for saving into public Downloads). */
    private val _fileReadyToSave = MutableSharedFlow<Pair<DriveFileEntity, File>>(extraBufferCapacity = 4)
    val fileReadyToSave: SharedFlow<Pair<DriveFileEntity, File>> = _fileReadyToSave

    /** Downloaded, ready to hand off to Android's system share sheet. */
    private val _fileReadyToShare = MutableSharedFlow<Pair<DriveFileEntity, File>>(extraBufferCapacity = 4)
    val fileReadyToShare: SharedFlow<Pair<DriveFileEntity, File>> = _fileReadyToShare

    /** Downloaded, ready to hand off to an "Open with…" app chooser. */
    private val _fileReadyToOpenWith = MutableSharedFlow<Pair<DriveFileEntity, File>>(extraBufferCapacity = 4)
    val fileReadyToOpenWith: SharedFlow<Pair<DriveFileEntity, File>> = _fileReadyToOpenWith

    /** A generated t.me message link, ready to copy to the clipboard. */
    private val _linkReady = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val linkReady: SharedFlow<String> = _linkReady

    private val _errorMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessages: SharedFlow<String> = _errorMessages

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _accountDisplayName = MutableStateFlow<String?>(null)
    val accountDisplayName: StateFlow<String?> = _accountDisplayName.asStateFlow()

    private val _storageUsedBytes = MutableStateFlow<Long?>(null)
    val storageUsedBytes: StateFlow<Long?> = _storageUsedBytes.asStateFlow()

    init {
        viewModelScope.launch {
            authState.collect { state ->
                if (state is AuthState.Ready) {
                    refresh()
                    loadAccountInfo()
                }
            }
        }
        viewModelScope.launch {
            currentPath.collect { path -> refresh(path) }
        }
    }

    private fun loadAccountInfo() = viewModelScope.launch {
        runCatching { repository.getMe() }.onSuccess { user ->
            val name = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")
            _accountDisplayName.value = name.ifBlank { user.phoneNumber }
        }
    }

    fun loadStorageUsed() = viewModelScope.launch {
        _storageUsedBytes.value = null
        runCatching { repository.getTotalStorageBytes() }
            .onSuccess { _storageUsedBytes.value = it }
            .onFailure { _errorMessages.emit(it.message ?: "Storage lookup failed") }
    }

    fun logOut(onComplete: () -> Unit) = viewModelScope.launch {
        runCatching { repository.logOut() }
            .onFailure { _errorMessages.emit(it.message ?: "Logout failed") }
        onComplete()
    }

    fun submitPhone(phone: String) = viewModelScope.launch { telegram.submitPhoneNumber(phone) }
    fun submitCode(code: String) = viewModelScope.launch { telegram.submitCode(code) }
    fun submitPassword(password: String) = viewModelScope.launch { telegram.submitPassword(password) }

    fun refresh() = refresh(currentPath.value)
    private fun refresh(path: String) = viewModelScope.launch {
        _isRefreshing.value = true
        repository.refreshFolder(path)
        _isRefreshing.value = false
    }

    fun createFolder(name: String) = viewModelScope.launch {
        runCatching { repository.createFolder(currentPath.value, name) }
            .onFailure { _errorMessages.emit(it.message ?: "Create folder failed") }
    }

    fun openFolder(name: String) {
        _currentPath.value = repository.joinPath(currentPath.value, name)
    }

    fun navigateUp(): Boolean {
        if (currentPath.value == "/") return false
        _currentPath.value = repository.parentOf(currentPath.value)
        return true
    }

    // ---- Upload (single or multiple, each tracked independently) ----

    fun upload(files: List<File>) {
        val path = currentPath.value
        files.forEach { file ->
            viewModelScope.launch { uploadOne(file, path) }
        }
    }

    private suspend fun uploadOne(file: File, path: String) {
        _uploadProgress.update { it + (file.name to 0f) }
        runCatching {
            val fileId = repository.uploadFile(file, path)
            telegram.fileProgress
                .map { it[fileId] }
                .filterNotNull()
                .takeWhile { !it.completed }
                .collect { p -> _uploadProgress.update { m -> m + (file.name to p.progress) } }
        }.onFailure { _errorMessages.emit(it.message ?: "Upload failed: ${file.name}") }
        _uploadProgress.update { it - file.name }
        repository.refreshFolder(path)
    }

    // ---- Download (open, save-to-device, or batch) with live progress ----

    fun openFile(entity: DriveFileEntity) = viewModelScope.launch {
        downloadWithProgress(entity) { _fileReadyToOpen.emit(entity to it) }
    }

    fun downloadFile(entity: DriveFileEntity) = viewModelScope.launch {
        downloadWithProgress(entity) { _fileReadyToSave.emit(entity to it) }
    }

    fun downloadSelected() {
        val targets = filesInCurrentFolder.value.filter { it.messageId in selectedIds.value }
        targets.forEach { entity ->
            viewModelScope.launch { downloadWithProgress(entity) { _fileReadyToSave.emit(entity to it) } }
        }
        clearSelection()
    }

    fun shareFile(entity: DriveFileEntity) = viewModelScope.launch {
        downloadWithProgress(entity) { _fileReadyToShare.emit(entity to it) }
    }

    fun openFileWith(entity: DriveFileEntity) = viewModelScope.launch {
        downloadWithProgress(entity) { _fileReadyToOpenWith.emit(entity to it) }
    }

    fun copyLink(entity: DriveFileEntity) = viewModelScope.launch {
        runCatching { repository.getShareLink(entity) }
            .onSuccess { _linkReady.emit(it) }
            .onFailure { _errorMessages.emit(it.message ?: "Link generation failed") }
    }

    private suspend fun downloadWithProgress(entity: DriveFileEntity, onReady: suspend (File) -> Unit) {
        _downloadProgress.update { it + (entity.messageId to 0f) }
        val watcher = viewModelScope.launch {
            telegram.fileProgress
                .map { it[entity.telegramFileId] }
                .filterNotNull()
                .collect { p -> _downloadProgress.update { m -> m + (entity.messageId to p.progress) } }
        }
        runCatching { repository.downloadFile(entity) }
            .onSuccess { onReady(it) }
            .onFailure { _errorMessages.emit(it.message ?: "Download failed") }
        watcher.cancel()
        _downloadProgress.update { it - entity.messageId }
    }

    // ---- Rename / delete / selection ----

    fun deleteFile(entity: DriveFileEntity) = viewModelScope.launch {
        runCatching { repository.deleteFile(entity) }
            .onFailure { _errorMessages.emit(it.message ?: "Delete failed") }
    }

    fun deleteSelected() = viewModelScope.launch {
        val targets = filesInCurrentFolder.value.filter { it.messageId in selectedIds.value }
        targets.forEach { entity ->
            runCatching { repository.deleteFile(entity) }
                .onFailure { _errorMessages.emit(it.message ?: "Delete failed: ${entity.fileName}") }
        }
        clearSelection()
    }

    // ---- Trash ----

    val trashFiles: StateFlow<List<DriveFileEntity>> =
        repository.observeFolder(TRASH_PATH)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshTrash() = viewModelScope.launch { repository.refreshFolder(TRASH_PATH) }

    fun restoreFile(entity: DriveFileEntity) = viewModelScope.launch {
        runCatching { repository.restoreFile(entity) }
            .onFailure { _errorMessages.emit(it.message ?: "Restore failed") }
    }

    fun permanentlyDeleteFile(entity: DriveFileEntity) = viewModelScope.launch {
        runCatching { repository.permanentlyDeleteFile(entity) }
            .onFailure { _errorMessages.emit(it.message ?: "Delete failed") }
    }

    fun emptyTrash() = viewModelScope.launch {
        runCatching { repository.emptyTrash() }
            .onFailure { _errorMessages.emit(it.message ?: "Empty trash failed") }
    }

    fun renameFile(entity: DriveFileEntity, newName: String) = viewModelScope.launch {
        runCatching { repository.renameFile(entity, newName) }
            .onFailure { _errorMessages.emit(it.message ?: "Rename failed") }
    }

    fun copyFile(entity: DriveFileEntity, destPath: String) = viewModelScope.launch {
        runCatching { repository.copyFile(entity, destPath) }
            .onFailure { _errorMessages.emit(it.message ?: "Copy failed") }
    }

    fun moveFile(entity: DriveFileEntity, destPath: String) = viewModelScope.launch {
        runCatching { repository.moveFile(entity, destPath) }
            .onFailure { _errorMessages.emit(it.message ?: "Move failed") }
        refresh()
    }

    fun moveSelected(destPath: String) = viewModelScope.launch {
        val targets = filesInCurrentFolder.value.filter { it.messageId in selectedIds.value }
        targets.forEach { entity ->
            runCatching { repository.moveFile(entity, destPath) }
                .onFailure { _errorMessages.emit(it.message ?: "Move failed: ${entity.fileName}") }
        }
        clearSelection()
        refresh()
    }

    fun copySelected(destPath: String) = viewModelScope.launch {
        val targets = filesInCurrentFolder.value.filter { it.messageId in selectedIds.value }
        targets.forEach { entity ->
            runCatching { repository.copyFile(entity, destPath) }
                .onFailure { _errorMessages.emit(it.message ?: "Copy failed: ${entity.fileName}") }
        }
        clearSelection()
    }

    fun toggleSelect(messageId: Long) = _selectedIds.update { current ->
        if (messageId in current) current - messageId else current + messageId
    }

    fun selectAll() {
        _selectedIds.value = filesInCurrentFolder.value.map { it.messageId }.toSet()
    }

    fun clearSelection() { _selectedIds.value = emptySet() }
}
