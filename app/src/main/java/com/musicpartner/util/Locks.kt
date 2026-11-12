package com.musicpartner.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/** Keeps the CPU and Wi-Fi awake while streaming with the screen off. */
class Locks(context: Context) {
    private val wake = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicPartner:stream")
        .apply { setReferenceCounted(false) }

    @Suppress("DEPRECATION")
    private val wifi = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MusicPartner:stream")
        .apply { setReferenceCounted(false) }

    @SuppressLint("WakelockTimeout")
    fun acquire() {
        wake.acquire()
        wifi.acquire()
    }

    fun release() {
        if (wake.isHeld) wake.release()
        if (wifi.isHeld) wifi.release()
    }
}
