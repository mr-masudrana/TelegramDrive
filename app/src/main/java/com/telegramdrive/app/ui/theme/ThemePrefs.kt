package com.telegramdrive.app.ui.theme

import android.content.Context

enum class ThemeMode(val label: String) {
    SYSTEM("সিস্টেম অনুযায়ী"),
    LIGHT("লাইট"),
    DARK("ডার্ক")
}

class ThemePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    var mode: ThemeMode
        get() = ThemeMode.entries.find { it.name == prefs.getString(KEY_MODE, null) } ?: ThemeMode.SYSTEM
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    companion object {
        private const val KEY_MODE = "mode"
    }
}
