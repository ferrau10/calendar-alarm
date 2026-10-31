package com.calendaralarm.data.auth.android

import android.content.Context
import com.calendaralarm.data.auth.AccountStore

class PrefsAccountStore(context: Context) : AccountStore {
    private val prefs = context.applicationContext.getSharedPreferences("account", Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY_EMAIL, null)

    override fun save(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_EMAIL).apply()
    }

    private companion object {
        const val KEY_EMAIL = "email"
    }
}
