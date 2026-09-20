package com.uci.pkbe

import android.content.Context
import java.util.UUID

object DeviceIdentity {
    private const val PREFS = "pkbe"
    private const val KEY = "deviceId"

    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString().lowercase()
        prefs.edit().putString(KEY, created).apply()
        return created
    }
}

object SessionStore {
    private const val PREFS = "pkbe"

    fun token(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("token", null)

    fun username(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("username", null)

    fun save(context: Context, token: String, username: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("token", token)
            .putString("username", username)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("token")
            .remove("username")
            .apply()
    }
}
