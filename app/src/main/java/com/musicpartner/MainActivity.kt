package com.musicpartner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.musicpartner.capture.CaptureService
import com.musicpartner.listen.ListenerService
import com.musicpartner.media.MediaHub
import com.musicpartner.ui.AppScreen
import com.musicpartner.ui.Actions

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private val notifAccess = mutableStateOf(false)
    private var pendingMode: String? = null

    private val recordAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingMode != null) {
                requestProjection()
            } else {
                pendingMode = null
                Session.message.value =
                    "Android needs the \"record audio\" permission to capture music from other apps. " +
                        "MusicPartner never uses the microphone."
            }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val mode = pendingMode ?: return@registerForActivityResult
            pendingMode = null
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                CaptureService.start(this, result.resultCode, data, mode, prefs.serverUrl)
            } else {
                Session.message.value = "Audio capture permission was not given."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val actions = object : Actions {
            override fun openNotificationAccess() {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }

            override fun saveServerUrl(url: String) {
                prefs.serverUrl = url
            }

            override fun startTest() = startCapture(CaptureService.MODE_TEST)

            override fun startHost() {
                if (!validServer()) return
                startCapture(CaptureService.MODE_HOST)
            }

            override fun join(code: String) {
                if (!validServer()) return
                val clean = code.trim().uppercase()
                if (clean.length < 4) {
                    Session.message.value = "Enter the room code your friend sees on their phone."
                    return
                }
                prefs.lastCode = clean
                ListenerService.start(this@MainActivity, prefs.serverUrl, clean)
            }

            override fun command(action: String) {
                when (Session.role.value) {
                    Role.LISTENER -> ListenerService.command(this@MainActivity, action)
                    Role.HOST, Role.TEST -> CaptureService.command(this@MainActivity, action)
                    Role.NONE -> Unit
                }
            }

            override fun stop() {
                when (Session.role.value) {
                    Role.LISTENER -> ListenerService.stop(this@MainActivity)
                    Role.HOST, Role.TEST -> CaptureService.stop(this@MainActivity)
                    Role.NONE -> Unit
                }
            }
        }

        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    AppScreen(
                        notifAccess = notifAccess.value,
                        initialServerUrl = prefs.serverUrl,
                        initialCode = prefs.lastCode,
                        actions = actions,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notifAccess.value = MediaHub.hasAccess(this)
    }

    private fun validServer(): Boolean {
        val url = prefs.serverUrl
        if (url.startsWith("ws://") || url.startsWith("wss://")) return true
        Session.message.value = "Set the server address first, e.g. wss://your-relay.onrender.com or ws://192.168.1.5:8080"
        return false
    }

    private fun startCapture(mode: String) {
        pendingMode = mode
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            requestProjection()
        } else {
            recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            // Force "entire screen" so the system doesn't offer single-app sharing.
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        projectionLauncher.launch(intent)
    }
}
