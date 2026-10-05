package com.telegramdrive.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A "folder" doesn't really exist in Telegram — this row is a local cache of a
 * marker text message (tagged #folderdef:<path>) sent to the channel, so folder
 * existence survives reinstalls/other devices just like files do.
 */
@Entity(tableName = "drive_folders")
data class DriveFolderEntity(
    @PrimaryKey val messageId: Long,
    val chatId: Long,
    val path: String,       // e.g. "/Photos/2026"
    val parentPath: String, // e.g. "/Photos"
    val name: String        // e.g. "2026"
)
