package com.musicpartner

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("musicpartner", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("server_url", "") ?: ""
        set(value) = sp.edit().putString("server_url", value.trim()).apply()

    var lastCode: String
        get() = sp.getString("last_code", "") ?: ""
        set(value) = sp.edit().putString("last_code", value.trim().uppercase()).apply()
}
