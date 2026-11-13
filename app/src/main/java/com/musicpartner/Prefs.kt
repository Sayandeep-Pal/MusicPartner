package com.musicpartner

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("musicpartner", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("server_url", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER
        set(value) = sp.edit().putString("server_url", value.trim()).apply()

    var lastCode: String
        get() = sp.getString("last_code", "") ?: ""
        set(value) = sp.edit().putString("last_code", value.trim().uppercase()).apply()

    companion object {
        /** Public relay deployed on Railway (see plan.md). Users can still type another address. */
        const val DEFAULT_SERVER = "wss://musicpartner-production.up.railway.app"
    }
}
