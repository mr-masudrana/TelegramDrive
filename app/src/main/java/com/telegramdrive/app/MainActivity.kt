package com.telegramdrive.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegramdrive.app.backup.AutoBackupPrefs
import com.telegramdrive.app.backup.BackupScheduler
import com.telegramdrive.app.data.AppDatabase
import com.telegramdrive.app.data.DriveFileEntity
import com.telegramdrive.app.repository.DriveRepository
import com.telegramdrive.app.telegram.AuthState
import com.telegramdrive.app.ui.DriveViewModel
import com.telegramdrive.app.ui.screens.AccountScreen
import com.telegramdrive.app.ui.screens.FileListScreen
import com.telegramdrive.app.ui.screens.FileViewerScreen
import com.telegramdrive.app.ui.screens.LoginScreen
import com.telegramdrive.app.ui.screens.TrashScreen
import com.telegramdrive.app.ui.theme.TelegramDriveTheme
import com.telegramdrive.app.ui.theme.ThemeMode
import com.telegramdrive.app.ui.theme.ThemePrefs
import java.io.File
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {

    private val mediaPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val app = application as TelegramDriveApp
        val db = AppDatabase.get(this)
        val repository = DriveRepository(
            telegram = app.telegramClient,
            fileDao = db.driveFileDao(),
            folderDao = db.driveFolderDao()
        )
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DriveViewModel(app.telegramClient, repository) as T
        }
        val backupPrefs = AutoBackupPrefs(this)
        if (backupPrefs.isEnabled) BackupScheduler.start(this)
        val themePrefs = ThemePrefs(this)

        setContent {
            var themeMode by remember { mutableStateOf(themePrefs.mode) }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            TelegramDriveTheme(darkTheme = darkTheme) {
            Surface(color = MaterialTheme.colorScheme.background) {
            val viewModel: DriveViewModel = viewModel(factory = factory)
            val authState by viewModel.authState.collectAsState()
            val currentPath by viewModel.currentPath.collectAsState()
            val subFolders by viewModel.subFoldersInCurrentFolder.collectAsState()
            val files by viewModel.filesInCurrentFolder.collectAsState()
            val uploadProgress by viewModel.uploadProgress.collectAsState()
            val downloadProgress by viewModel.downloadProgress.collectAsState()
            val selectedIds by viewModel.selectedIds.collectAsState()
            val isRefreshing by viewModel.isRefreshing.collectAsState()
            var autoBackupEnabled by remember { mutableStateOf(backupPrefs.isEnabled) }
            var viewingFile by remember { mutableStateOf<Pair<DriveFileEntity, File>?>(null) }
            var showAccountScreen by remember { mutableStateOf(false) }
            var showTrashScreen by remember { mutableStateOf(false) }
            val trashFiles by viewModel.trashFiles.collectAsState()
            val accountDisplayName by viewModel.accountDisplayName.collectAsState()
            val storageUsedBytes by viewModel.storageUsedBytes.collectAsState()
            val searchActive by viewModel.searchActive.collectAsState()
            val searchQuery by viewModel.searchQuery.collectAsState()
            val searchResults by viewModel.searchResults.collectAsState()
            val isSearchLoading by viewModel.isSearchLoading.collectAsState()
            val sortOption by viewModel.sortOption.collectAsState()
            val typeFilter by viewModel.typeFilter.collectAsState()

            val pickFiles = rememberLauncherForActivityResult(
                ActivityResultContracts.GetMultipleContents()
            ) { uris: List<Uri> ->
                if (uris.isNotEmpty()) copyAndUpload(uris, viewModel)
            }

            val requestMediaPermissions = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                if (grants.values.all { it }) {
                    backupPrefs.isEnabled = true
                    autoBackupEnabled = true
                    BackupScheduler.start(this@MainActivity)
                } else {
                    Toast.makeText(this@MainActivity, "অটো-ব্যাকআপের জন্য ফটো/ভিডিও পারমিশন দরকার", Toast.LENGTH_LONG).show()
                }
            }

            val backEnabled = showAccountScreen || showTrashScreen || viewingFile != null ||
                searchActive || currentPath != "/" || selectedIds.isNotEmpty()
            var backProgress by remember { mutableStateOf(0f) }

            PredictiveBackHandler(enabled = backEnabled) { progress ->
                try {
                    progress.collect { event -> backProgress = event.progress }
                    // সোয়াইপ সম্পূর্ণ হলে (ছেড়ে দিলে) আসল "back" অ্যাকশন হয়
                    when {
                        showAccountScreen -> showAccountScreen = false
                        showTrashScreen -> showTrashScreen = false
                        viewingFile != null -> viewingFile = null
                        searchActive -> viewModel.closeSearch()
                        selectedIds.isNotEmpty() -> viewModel.clearSelection()
                        else -> viewModel.navigateUp()
                    }
                } catch (e: CancellationException) {
                    // মাঝপথে সোয়াইপ বাতিল হলে — কিছু করার দরকার নেই, নিচের finally state রিসেট করে দেবে
                } finally {
                    backProgress = 0f
                }
            }

            LaunchedEffect(Unit) {
                viewModel.fileReadyToOpen.collect { pair -> viewingFile = pair }
            }
            LaunchedEffect(Unit) {
                viewModel.fileReadyToSave.collect { (entity, file) -> saveToPublicDownloads(entity, file) }
            }
            LaunchedEffect(Unit) {
                viewModel.fileReadyToShare.collect { (entity, file) -> shareWithSystemSheet(entity, file) }
            }
            LaunchedEffect(Unit) {
                viewModel.fileReadyToOpenWith.collect { (_, file) -> openWithSystemApp(file) }
            }
            LaunchedEffect(Unit) {
                viewModel.linkReady.collect { link -> copyLinkToClipboard(link) }
            }
            LaunchedEffect(Unit) {
                viewModel.errorMessages.collect { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }
            }

            Crossfade(
                modifier = Modifier.graphicsLayer {
                    val scale = 1f - (backProgress * 0.08f)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - (backProgress * 0.25f)
                },
                targetState = when {
                    showAccountScreen -> "account"
                    showTrashScreen -> "trash"
                    viewingFile != null -> "viewer"
                    authState == AuthState.Ready -> "files"
                    else -> "login"
                },
                label = "screen"
            ) { screen ->
                when (screen) {
                    "trash" -> TrashScreen(
                        files = trashFiles,
                        onClose = { showTrashScreen = false },
                        onRefresh = viewModel::refreshTrash,
                        onRestore = viewModel::restoreFile,
                        onDeletePermanently = viewModel::permanentlyDeleteFile,
                        onEmptyTrash = viewModel::emptyTrash
                    )
                    "account" -> AccountScreen(
                        accountDisplayName = accountDisplayName,
                        storageUsedBytes = storageUsedBytes,
                        accounts = app.accountStore.listAccounts(),
                        activeAccountId = app.accountStore.activeAccountId(),
                        onClose = { showAccountScreen = false },
                        onRefreshStorage = viewModel::loadStorageUsed,
                        onSwitchAccount = { id -> switchAccount(id) },
                        onAddAccount = { addAccount() },
                        onRemoveAccount = { id -> app.accountStore.removeAccount(id) },
                        onLogOut = { viewModel.logOut { restartApp() } },
                        themeMode = themeMode,
                        onThemeModeChange = { mode -> themeMode = mode; themePrefs.mode = mode }
                    )
                    "viewer" -> {
                        val (entity, file) = viewingFile!!
                        FileViewerScreen(
                            entity = entity,
                            file = file,
                            onClose = { viewingFile = null },
                            onOpenExternally = { openWithSystemApp(file) }
                        )
                    }
                    "files" -> FileListScreen(
                        currentPath = currentPath,
                        subFolders = subFolders,
                        files = files,
                        uploadProgress = uploadProgress,
                        downloadProgress = downloadProgress,
                        selectedIds = selectedIds,
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh,
                        onUploadClick = { pickFiles.launch("*/*") },
                        onOpenFile = viewModel::openFile,
                        onOpenWith = viewModel::openFileWith,
                        onDownloadFile = viewModel::downloadFile,
                        onShareFile = viewModel::shareFile,
                        onCopyLink = viewModel::copyLink,
                        onCopyFile = viewModel::copyFile,
                        onMoveFile = viewModel::moveFile,
                        onDeleteFile = viewModel::deleteFile,
                        onRenameFile = viewModel::renameFile,
                        onOpenFolder = viewModel::openFolder,
                        onNavigateUp = { viewModel.navigateUp() },
                        onCreateFolder = viewModel::createFolder,
                        onToggleSelect = viewModel::toggleSelect,
                        onSelectAll = viewModel::selectAll,
                        onClearSelection = viewModel::clearSelection,
                        onDownloadSelected = viewModel::downloadSelected,
                        onDeleteSelected = viewModel::deleteSelected,
                        onCopySelected = viewModel::copySelected,
                        onMoveSelected = viewModel::moveSelected,
                        onOpenAccount = { showAccountScreen = true },
                        onOpenTrash = { showTrashScreen = true },
                        searchActive = searchActive,
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        isSearchLoading = isSearchLoading,
                        onOpenSearch = viewModel::openSearch,
                        onCloseSearch = viewModel::closeSearch,
                        onSearchQueryChange = viewModel::setSearchQuery,
                        sortOption = sortOption,
                        onSortOptionChange = viewModel::setSortOption,
                        typeFilter = typeFilter,
                        onTypeFilterChange = viewModel::setTypeFilter,
                        autoBackupEnabled = autoBackupEnabled,
                        onToggleAutoBackup = { enable ->
                            if (enable) {
                                requestMediaPermissions.launch(mediaPermissions)
                            } else {
                                backupPrefs.isEnabled = false
                                autoBackupEnabled = false
                                BackupScheduler.stop(this@MainActivity)
                            }
                        }
                    )
                    else -> LoginScreen(
                        state = authState,
                        onPhone = viewModel::submitPhone,
                        onCode = viewModel::submitCode,
                        onPassword = viewModel::submitPassword
                    )
                }
            }
            }
            }
        }
    }

    /** Content picker gives content:// Uris; TDLib needs real file paths, so copy each to cache first. */
    private fun copyAndUpload(uris: List<Uri>, viewModel: DriveViewModel) {
        val files = uris.mapNotNull { uri ->
            runCatching {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "upload_${System.currentTimeMillis()}"
                val target = File(cacheDir, "${System.currentTimeMillis()}_$name")
                contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target
            }.getOrNull()
        }
        viewModel.upload(files)
    }

    private fun openWithSystemApp(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val mimeType = mimeTypeFor(file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, "এই দিয়ে খুলুন")) }
            .onFailure { Toast.makeText(this, "এই ফাইল ওপেন করার মতো কোনো অ্যাপ পাওয়া যায়নি", Toast.LENGTH_LONG).show() }
    }

    /** Copies a TDLib-downloaded file into the device's public Downloads folder so it's visible outside the app. */
    private fun saveToPublicDownloads(entity: DriveFileEntity, file: File) {
        runCatching {
            val mimeType = mimeTypeFor(file)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, entity.fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create download entry")
                contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val target = File(downloadsDir, entity.fileName)
                file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }.onSuccess {
            Toast.makeText(this, "ডাউনলোড হয়েছে: ${entity.fileName}", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "সেভ করা যায়নি: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun mimeTypeFor(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"

    private fun shareWithSystemSheet(entity: DriveFileEntity, file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeFor(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, entity.fileName))
    }

    private fun copyLinkToClipboard(link: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("TelegramDrive file link", link))
        Toast.makeText(this, "লিংক কপি হয়েছে", Toast.LENGTH_SHORT).show()
    }

    /** TDLib needs a fresh Client per account directory, so switching/adding an account restarts the app. */
    private fun switchAccount(accountId: String) {
        val app = application as TelegramDriveApp
        app.accountStore.setActiveAccountId(accountId)
        restartApp()
    }

    private fun addAccount() {
        val app = application as TelegramDriveApp
        val newId = app.accountStore.createNewAccountId("Account ${app.accountStore.listAccounts().size + 1}")
        app.accountStore.setActiveAccountId(newId)
        restartApp()
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
