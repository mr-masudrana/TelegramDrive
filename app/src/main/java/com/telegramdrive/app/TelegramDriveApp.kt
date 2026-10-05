package com.telegramdrive.app

import android.app.Application
import com.telegramdrive.app.account.AccountStore
import com.telegramdrive.app.telegram.TelegramClient

class TelegramDriveApp : Application() {

    lateinit var accountStore: AccountStore
        private set

    // Single shared TDLib client wrapper for the whole app — bound to the currently active account.
    lateinit var telegramClient: TelegramClient
        private set

    override fun onCreate() {
        super.onCreate()
        accountStore = AccountStore(this)
        telegramClient = TelegramClient(applicationContext, accountStore.activeAccountId())
    }
}
