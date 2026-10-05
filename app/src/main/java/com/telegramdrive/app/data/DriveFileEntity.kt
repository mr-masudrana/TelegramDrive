package com.telegramdrive.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local cache/index of files stored in the Telegram channel, so listing doesn't need a live fetch. */
@Entity(tableName = "drive_files")
data class DriveFileEntity(
    @PrimaryKey val messageId: Long,
    val chatId: Long,
    val fileName: String,
    val folderPath: String, // e.g. "/Photos/2026" — virtual folder emulated via caption
    val sizeBytes: Long,
    val mimeType: String?,
    val telegramFileId: Int,   // local/session file id — download-এর জন্য
    val remoteFileId: String,  // Telegram remote file id — কপি করার সময় re-upload ছাড়াই পুনরায় পাঠাতে ব্যবহার হয়
    val thumbnailBase64: String?, // ছোট প্রিভিউ ইমেজ (Telegram-এর minithumbnail), গ্রিড ভিউতে দ্রুত দেখানোর জন্য
    val trashedFromPath: String?, // ট্র্যাশে থাকলে আসল ফোল্ডার — রিস্টোর করার সময় লাগবে
    val uploadedAtEpochSec: Long
)
