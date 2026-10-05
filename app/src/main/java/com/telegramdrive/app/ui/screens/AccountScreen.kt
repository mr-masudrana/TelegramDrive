package com.telegramdrive.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telegramdrive.app.account.AccountInfo
import com.telegramdrive.app.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    accountDisplayName: String?,
    storageUsedBytes: Long?,
    accounts: List<AccountInfo>,
    activeAccountId: String,
    onClose: () -> Unit,
    onRefreshStorage: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onLogOut: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var accountToRemove by remember { mutableStateOf<AccountInfo?>(null) }

    LaunchedEffect(Unit) { onRefreshStorage() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("অ্যাকাউন্ট") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(accountDisplayName ?: "…", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    storageUsedBytes?.let { formatBytes(it) } ?: "হিসাব করা হচ্ছে…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("থিম", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == themeMode, onClick = { onThemeModeChange(mode) })
                        Text(mode.label)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("অ্যাকাউন্টসমূহ", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }

            items(accounts, key = { it.id }) { account ->
                val isActive = account.id == activeAccountId
                ListItem(
                    headlineContent = { Text(account.label) },
                    supportingContent = { if (isActive) Text("বর্তমানে সক্রিয়") },
                    leadingContent = {
                        RadioButton(selected = isActive, onClick = { if (!isActive) onSwitchAccount(account.id) })
                    },
                    trailingContent = {
                        if (!isActive) {
                            IconButton(onClick = { accountToRemove = account }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove account")
                            }
                        }
                    }
                )
                HorizontalDivider()
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAddAccount, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("নতুন অ্যাকাউন্ট যোগ করুন")
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("লগআউট", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("লগআউট করবেন?") },
            text = { Text("এই ডিভাইস থেকে এই Telegram অ্যাকাউন্টটি সাইন-আউট হয়ে যাবে। আপনার Telegram চ্যানেলের ফাইল অক্ষত থাকবে।") },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; onLogOut() }) { Text("লগআউট করুন") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("বাতিল") } }
        )
    }

    accountToRemove?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToRemove = null },
            title = { Text("\"${account.label}\" রিমুভ করবেন?") },
            text = { Text("এই তালিকা থেকে সরে যাবে। ওই অ্যাকাউন্টের লোকাল লগইন সেশন এই ডিভাইসে থেকে যেতে পারে।") },
            confirmButton = {
                TextButton(onClick = { onRemoveAccount(account.id); accountToRemove = null }) { Text("রিমুভ করুন") }
            },
            dismissButton = { TextButton(onClick = { accountToRemove = null }) { Text("বাতিল") } }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> "%.2f GB ব্যবহৃত".format(gb)
        mb >= 1 -> "%.1f MB ব্যবহৃত".format(mb)
        else -> "%.0f KB ব্যবহৃত".format(kb)
    }
}
