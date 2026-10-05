package com.telegramdrive.app.account

import android.content.Context

data class AccountInfo(val id: String, val label: String)

/**
 * TDLib doesn't support switching accounts on a live [org.drinkless.tdlib.Client] —
 * each account needs its own database directory. So "switching" here means: change
 * which accountId is active, then restart the process so [com.telegramdrive.app.TelegramDriveApp]
 * builds a fresh TelegramClient pointed at that account's directory.
 */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    fun activeAccountId(): String {
        val existing = prefs.getString(KEY_ACTIVE, null)
        if (existing != null) return existing
        // প্রথমবার — একটা ডিফল্ট অ্যাকাউন্ট বানিয়ে দেয়
        val defaultId = "acct_${System.currentTimeMillis()}"
        addAccountId(defaultId, "Account 1")
        setActiveAccountId(defaultId)
        return defaultId
    }

    fun setActiveAccountId(id: String) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun listAccounts(): List<AccountInfo> {
        val ids = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        return ids.map { id -> AccountInfo(id, prefs.getString(labelKey(id), id) ?: id) }
            .sortedBy { it.label }
    }

    fun setLabel(id: String, label: String) {
        prefs.edit().putString(labelKey(id), label).apply()
    }

    /** Creates a new local profile id (caller should switch to it and restart to trigger fresh login). */
    fun createNewAccountId(label: String): String {
        val id = "acct_${System.currentTimeMillis()}"
        addAccountId(id, label)
        return id
    }

    fun removeAccount(id: String) {
        val ids = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        ids.remove(id)
        prefs.edit()
            .putStringSet(KEY_IDS, ids)
            .remove(labelKey(id))
            .apply()
    }

    private fun addAccountId(id: String, label: String) {
        val ids = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        ids.add(id)
        prefs.edit()
            .putStringSet(KEY_IDS, ids)
            .putString(labelKey(id), label)
            .apply()
    }

    private fun labelKey(id: String) = "label_$id"

    companion object {
        private const val KEY_ACTIVE = "active_account_id"
        private const val KEY_IDS = "account_ids"
    }
}
