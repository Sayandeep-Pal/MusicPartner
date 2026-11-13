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
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.musicpartner.capture.CaptureService
import com.musicpartner.listen.ListenerService
import com.musicpartner.media.MediaHub
import com.musicpartner.ui.AppScreen
import com.musicpartner.ui.Actions
import com.musicpartner.ui.CuteLoading
import com.musicpartner.ui.ModeChooser
import com.musicpartner.ui.StartStage
import com.musicpartner.ui.VibeId
import com.musicpartner.ui.VibeTheme
import com.musicpartner.ui.Vibes

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private val notifAccess = mutableStateOf(false)
    private val vibeId = mutableStateOf(VibeId.CLASSIC)
    private val stage = mutableStateOf(StartStage.MAIN)
    private var pendingMode: String? = null

    private val recordAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingMode != null) {
                requestProjection()
            } else {
                pendingMode = null
                Session.message.value =
                    "Android needs the \"record audio\" permission to capture music from other apps. " +
                        getString(R.string.app_name) + " never uses the microphone."
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
        // Princess / Techie looks are Lite-only; the Full edition keeps the classic look.
        vibeId.value = if (BuildConfig.FULL_EDITION) VibeId.CLASSIC else prefs.vibe
        // Lite: a fresh start shows the mode chooser, then a short loading screen.
        // Skipped when a session is already running or after a rotation.
        stage.value = when {
            BuildConfig.FULL_EDITION -> StartStage.MAIN
            Session.role.value != Role.NONE -> StartStage.MAIN
            savedInstanceState != null ->
                runCatching { StartStage.valueOf(savedInstanceState.getString(KEY_STAGE) ?: "") }.getOrDefault(StartStage.MAIN)
            else -> StartStage.CHOOSE
        }

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

            override fun setVibe(id: VibeId) {
                if (BuildConfig.FULL_EDITION) return
                prefs.vibe = id
                vibeId.value = id
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
            VibeTheme(Vibes.of(vibeId.value, isSystemInDarkTheme())) {
                Surface(
                    Modifier.fillMaxSize(),
                    color = if (vibeId.value == VibeId.CLASSIC) MaterialTheme.colorScheme.background else Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    when (stage.value) {
                        StartStage.CHOOSE -> ModeChooser { picked ->
                            actions.setVibe(picked)
                            stage.value = StartStage.LOADING
                        }
                        StartStage.LOADING -> CuteLoading(vibeId.value) { stage.value = StartStage.MAIN }
                        StartStage.MAIN -> AppScreen(
                            notifAccess = notifAccess.value,
                            initialServerUrl = prefs.serverUrl,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Don't replay the loading screen after a rotation.
        val st = if (stage.value == StartStage.LOADING) StartStage.MAIN else stage.value
        outState.putString(KEY_STAGE, st.name)
    }

    override fun onResume() {
        super.onResume()
        notifAccess.value = MediaHub.hasAccess(this)
    }

    private fun validServer(): Boolean {
        val url = prefs.serverUrl
        if (url.startsWith("ws://") || url.startsWith("wss://")) return true
        Session.message.value = "Set the server address first, e.g. ${Prefs.DEFAULT_SERVER}"
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

    private companion object {
        const val KEY_STAGE = "start_stage"
    }
}
