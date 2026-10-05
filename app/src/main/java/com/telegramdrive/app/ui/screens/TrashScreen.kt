package com.telegramdrive.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telegramdrive.app.data.DriveFileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    files: List<DriveFileEntity>,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (DriveFileEntity) -> Unit,
    onDeletePermanently: (DriveFileEntity) -> Unit,
    onEmptyTrash: () -> Unit
) {
    var fileToDelete by remember { mutableStateOf<DriveFileEntity?>(null) }
    var showEmptyConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onRefresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ট্র্যাশ") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (files.isNotEmpty()) {
                        IconButton(onClick = { showEmptyConfirm = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Empty trash")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("ট্র্যাশ খালি")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(files, key = { it.messageId }) { file ->
                    ListItem(
                        headlineContent = { Text(file.fileName) },
                        supportingContent = { Text("আগে ছিল: ${file.trashedFromPath ?: "/"}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onRestore(file) }) {
                                    Icon(Icons.Filled.Restore, contentDescription = "Restore")
                                }
                                IconButton(onClick = { fileToDelete = file }) {
                                    Icon(Icons.Filled.DeleteForever, contentDescription = "Delete forever")
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("স্থায়ীভাবে ডিলিট করবেন?") },
            text = { Text("\"${file.fileName}\" চিরতরে মুছে যাবে, আর ফিরিয়ে আনা যাবে না।") },
            confirmButton = {
                TextButton(onClick = { onDeletePermanently(file); fileToDelete = null }) { Text("ডিলিট করুন") }
            },
            dismissButton = { TextButton(onClick = { fileToDelete = null }) { Text("বাতিল") } }
        )
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text("ট্র্যাশ খালি করবেন?") },
            text = { Text("ট্র্যাশের সবগুলো ফাইল চিরতরে মুছে যাবে।") },
            confirmButton = {
                TextButton(onClick = { onEmptyTrash(); showEmptyConfirm = false }) { Text("খালি করুন") }
            },
            dismissButton = { TextButton(onClick = { showEmptyConfirm = false }) { Text("বাতিল") } }
        )
    }
}
