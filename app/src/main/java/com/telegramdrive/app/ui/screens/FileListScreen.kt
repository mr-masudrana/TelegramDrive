package com.telegramdrive.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.animateItemPlacement
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.telegramdrive.app.data.DriveFileEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileListScreen(
    currentPath: String,
    subFolders: List<String>,
    files: List<DriveFileEntity>,
    uploadProgress: Map<String, Float>,
    downloadProgress: Map<Long, Float>,
    selectedIds: Set<Long>,
    onRefresh: () -> Unit,
    onUploadClick: () -> Unit,
    onOpenFile: (DriveFileEntity) -> Unit,
    onOpenWith: (DriveFileEntity) -> Unit,
    onDownloadFile: (DriveFileEntity) -> Unit,
    onShareFile: (DriveFileEntity) -> Unit,
    onCopyLink: (DriveFileEntity) -> Unit,
    onCopyFile: (DriveFileEntity, String) -> Unit,
    onMoveFile: (DriveFileEntity, String) -> Unit,
    onDeleteFile: (DriveFileEntity) -> Unit,
    onRenameFile: (DriveFileEntity, String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCopySelected: (String) -> Unit,
    onMoveSelected: (String) -> Unit,
    autoBackupEnabled: Boolean,
    onToggleAutoBackup: (Boolean) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenTrash: () -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    searchResults: List<DriveFileEntity>,
    isSearchLoading: Boolean,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    typeFilter: FileTypeFilter,
    onTypeFilterChange: (FileTypeFilter) -> Unit,
    isRefreshing: Boolean = false
) {
    var fileToRename by remember { mutableStateOf<DriveFileEntity?>(null) }
    var fileToDelete by remember { mutableStateOf<DriveFileEntity?>(null) }
    var fileForDetails by remember { mutableStateOf<DriveFileEntity?>(null) }
    var fileToCopy by remember { mutableStateOf<DriveFileEntity?>(null) }
    var fileToMove by remember { mutableStateOf<DriveFileEntity?>(null) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var showCopySelectedDialog by remember { mutableStateOf(false) }
    var showMoveSelectedDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showSortFilterDialog by remember { mutableStateOf(false) }
    var fabMenuOpen by remember { mutableStateOf(false) }
    var selectionMenuOpen by remember { mutableStateOf(false) }
    var isGridView by rememberSaveable { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    Scaffold(
        topBar = {
            when {
                selectionMode -> {
                TopAppBar(
                    title = { Text("${selectedIds.size} সিলেক্টেড") },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Filled.CheckBox, contentDescription = "Select all")
                        }
                        IconButton(onClick = onDownloadSelected) {
                            Icon(Icons.Filled.Download, contentDescription = "Download selected")
                        }
                        Box {
                            IconButton(onClick = { selectionMenuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = selectionMenuOpen, onDismissRequest = { selectionMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("কপি") },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                    onClick = { selectionMenuOpen = false; showCopySelectedDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("মুভ") },
                                    leadingIcon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null) },
                                    onClick = { selectionMenuOpen = false; showMoveSelectedDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("ডিলিট") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = { selectionMenuOpen = false; showDeleteSelectedConfirm = true }
                                )
                            }
                        }
                    }
                )
                }
                searchActive -> {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("ফাইলের নাম দিয়ে খুঁজুন…") }
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onCloseSearch) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Close search")
                            }
                        }
                    )
                }
                else -> {
                    TopAppBar(
                        title = { Text(if (currentPath == "/") "My Drive" else currentPath) },
                        navigationIcon = {
                            if (currentPath != "/") {
                                IconButton(onClick = onNavigateUp) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = onOpenSearch) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = { showSortFilterDialog = true }) {
                                Icon(Icons.Filled.Sort, contentDescription = "Sort & filter")
                            }
                            IconButton(onClick = { onToggleAutoBackup(!autoBackupEnabled) }) {
                                Icon(
                                    Icons.Filled.Backup,
                                    contentDescription = "Auto-backup",
                                    tint = if (autoBackupEnabled) MaterialTheme.colorScheme.primary
                                           else LocalContentColor.current.copy(alpha = 0.5f)
                                )
                            }
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                            IconButton(onClick = onOpenAccount) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = "Account")
                            }
                            IconButton(onClick = onOpenTrash) {
                                Icon(Icons.Filled.Delete, contentDescription = "Trash")
                            }
                            IconButton(onClick = { isGridView = !isGridView }) {
                                Icon(
                                    if (isGridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                                    contentDescription = "Toggle view"
                                )
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                Box {
                    FloatingActionButton(onClick = { fabMenuOpen = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                    DropdownMenu(expanded = fabMenuOpen, onDismissRequest = { fabMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("ফাইল আপলোড (একাধিক বাছাই করা যায়)") },
                            leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                            onClick = { fabMenuOpen = false; onUploadClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("নতুন ফোল্ডার") },
                            leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                            onClick = { fabMenuOpen = false; showCreateFolderDialog = true }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchActive) {
                SearchResultsSection(
                    query = searchQuery,
                    results = searchResults,
                    isLoading = isSearchLoading,
                    downloadProgress = downloadProgress,
                    onOpenFile = onOpenFile,
                    onOpenWith = onOpenWith,
                    onDownloadFile = onDownloadFile,
                    onShareFile = onShareFile,
                    onCopyLink = onCopyLink,
                    onDeleteFile = onDeleteFile,
                    onDetails = { fileForDetails = it }
                )
                return@Column
            }
            AnimatedVisibility(visible = isRefreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (uploadProgress.isNotEmpty()) UploadProgressSection(uploadProgress)

            if (subFolders.isEmpty() && files.isEmpty() && uploadProgress.isEmpty()) {
                AnimatedVisibility(
                    visible = !isRefreshing,
                    enter = fadeIn(tween(300))
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "এই ফোল্ডার খালি",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "+ বাটনে চাপ দিয়ে আপলোড করুন বা নতুন ফোল্ডার বানান",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(subFolders, key = { "folder:$it" }, span = { GridItemSpan(1) }) { name ->
                        FolderGridCell(name = name, onClick = { onOpenFolder(name) })
                    }
                    items(files, key = { it.messageId }) { file ->
                        FileGridCell(
                            file = file,
                            isSelected = file.messageId in selectedIds,
                            selectionMode = selectionMode,
                            progress = downloadProgress[file.messageId],
                            onOpen = { onOpenFile(file) },
                            onToggleSelect = { onToggleSelect(file.messageId) },
                            onLongClick = { onToggleSelect(file.messageId) }
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(subFolders, key = { "folder:$it" }) { name ->
                        ListItem(
                            modifier = Modifier
                                .animateItemPlacement()
                                .combinedClickable(onClick = { onOpenFolder(name) }),
                            leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            headlineContent = { Text(name) }
                        )
                        HorizontalDivider()
                    }
                    items(files, key = { it.messageId }) { file ->
                        FileRow(
                            modifier = Modifier.animateItemPlacement(),
                            file = file,
                            isSelected = file.messageId in selectedIds,
                            selectionMode = selectionMode,
                            progress = downloadProgress[file.messageId],
                            onOpen = { onOpenFile(file) },
                            onOpenWith = { onOpenWith(file) },
                            onDownload = { onDownloadFile(file) },
                            onShare = { onShareFile(file) },
                            onCopyLink = { onCopyLink(file) },
                            onCopy = { fileToCopy = file },
                            onMove = { fileToMove = file },
                            onRename = { fileToRename = file },
                            onDelete = { fileToDelete = file },
                            onDetails = { fileForDetails = file },
                            onToggleSelect = { onToggleSelect(file.messageId) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        TextInputDialog(
            title = "নতুন ফোল্ডার",
            placeholder = "ফোল্ডারের নাম",
            confirmLabel = "তৈরি করুন",
            onConfirm = { name -> onCreateFolder(name); showCreateFolderDialog = false },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    if (showSortFilterDialog) {
        SortFilterDialog(
            currentSort = sortOption,
            currentFilter = typeFilter,
            onSortChange = onSortOptionChange,
            onFilterChange = onTypeFilterChange,
            onDismiss = { showSortFilterDialog = false }
        )
    }

    fileToRename?.let { file ->
        TextInputDialog(
            title = "ফাইলের নাম বদলান",
            initialValue = file.fileName,
            confirmLabel = "সেভ",
            onConfirm = { newName -> onRenameFile(file, newName); fileToRename = null },
            onDismiss = { fileToRename = null }
        )
    }

    fileForDetails?.let { file ->
        FileDetailsDialog(
            file = file,
            onCopyLink = { onCopyLink(file) },
            onDismiss = { fileForDetails = null }
        )
    }

    fileToCopy?.let { file ->
        TextInputDialog(
            title = "\"${file.fileName}\" কোথায় কপি করবেন?",
            placeholder = "গন্তব্য পাথ, যেমন /Photos",
            initialValue = file.folderPath,
            confirmLabel = "কপি করুন",
            onConfirm = { path -> onCopyFile(file, path); fileToCopy = null },
            onDismiss = { fileToCopy = null }
        )
    }

    fileToMove?.let { file ->
        TextInputDialog(
            title = "\"${file.fileName}\" কোথায় মুভ করবেন?",
            placeholder = "গন্তব্য পাথ, যেমন /Photos",
            initialValue = file.folderPath,
            confirmLabel = "মুভ করুন",
            onConfirm = { path -> onMoveFile(file, path); fileToMove = null },
            onDismiss = { fileToMove = null }
        )
    }

    if (showCopySelectedDialog) {
        TextInputDialog(
            title = "${selectedIds.size}টা ফাইল কোথায় কপি করবেন?",
            placeholder = "গন্তব্য পাথ, যেমন /Photos",
            initialValue = currentPath,
            confirmLabel = "কপি করুন",
            onConfirm = { path -> onCopySelected(path); showCopySelectedDialog = false },
            onDismiss = { showCopySelectedDialog = false }
        )
    }

    if (showMoveSelectedDialog) {
        TextInputDialog(
            title = "${selectedIds.size}টা ফাইল কোথায় মুভ করবেন?",
            placeholder = "গন্তব্য পাথ, যেমন /Photos",
            initialValue = currentPath,
            confirmLabel = "মুভ করুন",
            onConfirm = { path -> onMoveSelected(path); showMoveSelectedDialog = false },
            onDismiss = { showMoveSelectedDialog = false }
        )
    }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("ফাইল ট্র্যাশে পাঠাবেন?") },
            text = { Text("\"${file.fileName}\" ট্র্যাশে চলে যাবে — চাইলে পরে ট্র্যাশ থেকে ফিরিয়ে আনতে পারবেন।") },
            confirmButton = {
                TextButton(onClick = { onDeleteFile(file); fileToDelete = null }) { Text("ট্র্যাশে পাঠান") }
            },
            dismissButton = { TextButton(onClick = { fileToDelete = null }) { Text("বাতিল") } }
        )
    }

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text("${selectedIds.size}টা ফাইল ট্র্যাশে পাঠাবেন?") },
            text = { Text("পরে ট্র্যাশ থেকে ফিরিয়ে আনতে পারবেন।") },
            confirmButton = {
                TextButton(onClick = { onDeleteSelected(); showDeleteSelectedConfirm = false }) { Text("ট্র্যাশে পাঠান") }
            },
            dismissButton = { TextButton(onClick = { showDeleteSelectedConfirm = false }) { Text("বাতিল") } }
        )
    }
}

@Composable
private fun UploadProgressSection(uploadProgress: Map<String, Float>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        uploadProgress.forEach { (name, progress) ->
            Text(name, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: DriveFileEntity,
    isSelected: Boolean,
    selectionMode: Boolean,
    progress: Float?,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleOverride: String? = null
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier) {
        ListItem(
            modifier = Modifier.combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                onLongClick = onToggleSelect
            ),
            leadingContent = if (selectionMode) {
                { Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() }) }
            } else null,
            headlineContent = { Text(file.fileName) },
            supportingContent = {
                Text(
                    subtitleOverride
                        ?: if (progress != null) "ডাউনলোড হচ্ছে… ${(progress * 100).toInt()}%" else formatSize(file.sizeBytes)
                )
            },
            trailingContent = {
                if (!selectionMode) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("ওপেন") }, onClick = { menuOpen = false; onOpen() })
                            DropdownMenuItem(
                                text = { Text("এই দিয়ে খুলুন") },
                                leadingIcon = { Icon(Icons.Filled.OpenInNew, contentDescription = null) },
                                onClick = { menuOpen = false; onOpenWith() }
                            )
                            DropdownMenuItem(
                                text = { Text("ডাউনলোড") },
                                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                onClick = { menuOpen = false; onDownload() }
                            )
                            DropdownMenuItem(
                                text = { Text("শেয়ার") },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = { menuOpen = false; onShare() }
                            )
                            DropdownMenuItem(
                                text = { Text("লিংক কপি") },
                                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                                onClick = { menuOpen = false; onCopyLink() }
                            )
                            DropdownMenuItem(
                                text = { Text("কপি") },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                onClick = { menuOpen = false; onCopy() }
                            )
                            DropdownMenuItem(
                                text = { Text("মুভ") },
                                leadingIcon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null) },
                                onClick = { menuOpen = false; onMove() }
                            )
                            DropdownMenuItem(
                                text = { Text("রিনেম") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { menuOpen = false; onRename() }
                            )
                            DropdownMenuItem(
                                text = { Text("সিলেক্ট") },
                                leadingIcon = { Icon(Icons.Filled.CheckBox, contentDescription = null) },
                                onClick = { menuOpen = false; onToggleSelect() }
                            )
                            DropdownMenuItem(
                                text = { Text("বিস্তারিত") },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                onClick = { menuOpen = false; onDetails() }
                            )
                            DropdownMenuItem(
                                text = { Text("ডিলিট") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = { menuOpen = false; onDelete() }
                            )
                        }
                    }
                }
            }
        )
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun FolderGridCell(name: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(4.dp))
        Text(name, maxLines = 1, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCell(
    file: DriveFileEntity,
    isSelected: Boolean,
    selectionMode: Boolean,
    progress: Float?,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit
) {
    val thumbnail = remember(file.thumbnailBase64) {
        file.thumbnailBase64?.let {
            runCatching {
                val bytes = android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                onLongClick = onLongClick
            )
    ) {
        Card(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        iconForMimeType(file.mimeType),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    )
                }
            }
        }
        Text(
            file.fileName,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                .padding(2.dp),
            color = androidx.compose.ui.graphics.Color.White
        )
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

private fun iconForMimeType(mimeType: String?): androidx.compose.ui.graphics.vector.ImageVector {
    val mime = mimeType.orEmpty()
    return when {
        mime == "application/pdf" -> Icons.Filled.PictureAsPdf
        mime.startsWith("video/") -> Icons.Filled.Movie
        mime.startsWith("audio/") -> Icons.Filled.AudioFile
        else -> Icons.Filled.InsertDriveFile
    }
}

@Composable
private fun FileDetailsDialog(file: DriveFileEntity, onCopyLink: () -> Unit, onDismiss: () -> Unit) {
    val dateFormatted = remember(file.uploadedAtEpochSec) {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        sdf.format(java.util.Date(file.uploadedAtEpochSec * 1000))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ফাইলের বিস্তারিত") },
        text = {
            Column {
                DetailRow("নাম", file.fileName)
                DetailRow("সাইজ", formatSize(file.sizeBytes))
                DetailRow("ধরন", file.mimeType ?: "অজানা")
                DetailRow("ফোল্ডার", file.folderPath)
                DetailRow("আপলোড", dateFormatted)
                if (file.trashedFromPath != null) {
                    DetailRow("ট্র্যাশে আসার আগে ছিল", file.trashedFromPath)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("বন্ধ করুন") } },
        dismissButton = {
            TextButton(onClick = { onCopyLink(); onDismiss() }) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("লিংক কপি")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
    placeholder: String? = null,
    confirmLabel: String = "ঠিক আছে"
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = placeholder?.let { { Text(it) } }
            )
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
private fun SearchResultsSection(
    query: String,
    results: List<DriveFileEntity>,
    isLoading: Boolean,
    downloadProgress: Map<Long, Float>,
    onOpenFile: (DriveFileEntity) -> Unit,
    onOpenWith: (DriveFileEntity) -> Unit,
    onDownloadFile: (DriveFileEntity) -> Unit,
    onShareFile: (DriveFileEntity) -> Unit,
    onCopyLink: (DriveFileEntity) -> Unit,
    onDeleteFile: (DriveFileEntity) -> Unit,
    onDetails: (DriveFileEntity) -> Unit
) {
    var fileToDelete by remember { mutableStateOf<DriveFileEntity?>(null) }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("পুরো ড্রাইভ ইনডেক্স করা হচ্ছে…")
            }
        }
        query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("ফাইলের নাম টাইপ করুন", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("\"$query\" এর সাথে মিলছে এমন কোনো ফাইল নেই")
        }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.messageId }) { file ->
                FileRow(
                    file = file,
                    isSelected = false,
                    selectionMode = false,
                    progress = downloadProgress[file.messageId],
                    onOpen = { onOpenFile(file) },
                    onOpenWith = { onOpenWith(file) },
                    onDownload = { onDownloadFile(file) },
                    onShare = { onShareFile(file) },
                    onCopyLink = { onCopyLink(file) },
                    onCopy = {},
                    onMove = {},
                    onRename = {},
                    onDelete = { fileToDelete = file },
                    onDetails = { onDetails(file) },
                    onToggleSelect = {},
                    subtitleOverride = "${file.folderPath} · ${formatSize(file.sizeBytes)}"
                )
                HorizontalDivider()
            }
        }
    }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("ফাইল ট্র্যাশে পাঠাবেন?") },
            text = { Text("\"${file.fileName}\" ট্র্যাশে চলে যাবে।") },
            confirmButton = {
                TextButton(onClick = { onDeleteFile(file); fileToDelete = null }) { Text("ট্র্যাশে পাঠান") }
            },
            dismissButton = { TextButton(onClick = { fileToDelete = null }) { Text("বাতিল") } }
        )
    }
}

@Composable
private fun SortFilterDialog(
    currentSort: SortOption,
    currentFilter: FileTypeFilter,
    onSortChange: (SortOption) -> Unit,
    onFilterChange: (FileTypeFilter) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("সর্ট ও ফিল্টার") },
        text = {
            Column {
                Text("সর্ট করুন", style = MaterialTheme.typography.labelLarge)
                SortOption.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == currentSort, onClick = { onSortChange(option) })
                        Text(option.label)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("ফাইলের ধরন", style = MaterialTheme.typography.labelLarge)
                FileTypeFilter.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == currentFilter, onClick = { onFilterChange(option) })
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("বন্ধ করুন") } }
    )
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(kb)
}
