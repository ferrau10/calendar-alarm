package com.calendaralarm.data.auth.android

import android.content.Context
import com.calendaralarm.data.auth.AccountStore

/** Connected accounts in SharedPreferences; migrates the single account saved by versions before 0.4. */
class PrefsAccountStore(context: Context) : AccountStore {
    private val prefs = context.applicationContext.getSharedPreferences("account", Context.MODE_PRIVATE)

    override fun load(): List<String> {
        val saved = prefs.getString(KEY_ACCOUNTS, null)
        if (saved != null) return saved.split(SEPARATOR).filter { it.isNotBlank() }
        return listOfNotNull(prefs.getString(KEY_LEGACY_EMAIL, null))
    }

    override fun save(accounts: List<String>) {
        prefs.edit().putString(KEY_ACCOUNTS, accounts.joinToString(SEPARATOR)).remove(KEY_LEGACY_EMAIL).apply()
    }

    private companion object {
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_LEGACY_EMAIL = "email"
        const val SEPARATOR = "\n"
    }
}
