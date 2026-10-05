package com.telegramdrive.app.backup

import android.content.Context

/** The Telegram-side "virtual folder" that auto-backed-up media gets uploaded into. */
const val BACKUP_FOLDER_PATH = "/Camera Backup"

class AutoBackupPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()
}
