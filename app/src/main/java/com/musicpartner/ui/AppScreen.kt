package com.musicpartner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicpartner.BuildConfig
import com.musicpartner.Conn
import com.musicpartner.NowPlaying
import com.musicpartner.Role
import com.musicpartner.Session
import kotlin.math.sqrt

interface Actions {
    fun openNotificationAccess()
    fun saveServerUrl(url: String)
    fun startTest()
    fun startHost()
    fun join(code: String)
    fun command(action: String)
    fun stop()
}

@Composable
fun AppScreen(notifAccess: Boolean, initialServerUrl: String, initialCode: String, actions: Actions) {
    val role by Session.role.collectAsState()
    val message by Session.message.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("MusicPartner", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Listen to the same song at the same time, from any music app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        message?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { Session.message.value = null }) { Text("OK") }
                }
            }
        }

        if (role == Role.NONE) {
            Home(notifAccess, initialServerUrl, initialCode, actions)
        } else {
            ActiveSession(role, notifAccess, actions)
        }
    }
}

@Composable
private fun Home(notifAccess: Boolean, initialServerUrl: String, initialCode: String, actions: Actions) {
    var server by remember { mutableStateOf(initialServerUrl) }
    var code by remember { mutableStateOf(initialCode) }

    SectionCard("Setup") {
        if (BuildConfig.FULL_EDITION) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (notifAccess) "✓ Song info enabled" else "Song info is off", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Optional. Shows the song name and app, and gives the most reliable play/pause/skip. " +
                            "Turn on \"MusicPartner\" in the list that opens.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!notifAccess) {
                    TextButton(onClick = actions::openNotificationAccess) { Text("Turn on") }
                }
            }
        } else {
            Text(
                "Lite edition: play, pause and skip work from both phones. Song names aren't shown.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HorizontalDivider()
        OutlinedTextField(
            value = server,
            onValueChange = {
                server = it
                actions.saveServerUrl(it)
            },
            label = { Text("Server address") },
            placeholder = { Text("wss://your-relay.onrender.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SectionCard("Share my music") {
        Text(
            "Play a song in YouTube Music, Spotify, YouTube or any app. Your friend hears it with you.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = actions::startHost, modifier = Modifier.fillMaxWidth()) { Text("Start sharing") }
        OutlinedButton(onClick = actions::startTest, modifier = Modifier.fillMaxWidth()) {
            Text("Test capture only (no internet)")
        }
    }

    SectionCard("Join a friend") {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
            label = { Text("Room code") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { actions.join(code) }, modifier = Modifier.fillMaxWidth()) { Text("Join") }
    }
}

@Composable
private fun ActiveSession(role: Role, notifAccess: Boolean, actions: Actions) {
    val conn by Session.conn.collectAsState()
    val roomCode by Session.roomCode.collectAsState()
    val listeners by Session.listeners.collectAsState()
    val level by Session.level.collectAsState()
    val verdict by Session.captureVerdict.collectAsState()
    val kbps by Session.kbps.collectAsState()
    val nowPlaying by Session.nowPlaying.collectAsState()
    val bufferMs by Session.bufferMs.collectAsState()
    val hostAway by Session.hostAway.collectAsState()

    when (role) {
        Role.HOST -> SectionCard("Sharing your music") {
            Text("Room code", style = MaterialTheme.typography.labelMedium)
            Text(
                roomCode ?: "······",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 6.sp,
            )
            Text("Send this code to your friend.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            StatusLine("Server", connText(conn))
            StatusLine("Friends listening", listeners.toString())
            StatusLine("Upload", "$kbps kbps")
        }
        Role.TEST -> SectionCard("Capture test") {
            Text(
                "Play music in the app you want to test, then come back here. " +
                    "If the bar moves, that app works with MusicPartner.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Role.LISTENER -> SectionCard("Listening with a friend") {
            StatusLine("Room", roomCode ?: "")
            StatusLine("Server", connText(conn))
            if (hostAway) {
                Text(
                    "Your friend's connection dropped. Waiting for them to come back…",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            StatusLine("Buffer", "$bufferMs ms")
        }
        Role.NONE -> Unit
    }

    if (role == Role.HOST || role == Role.TEST) {
        SectionCard("Captured audio") {
            LinearProgressIndicator(
                progress = { sqrt(level.coerceIn(0f, 1f)) },
                modifier = Modifier.fillMaxWidth().height(10.dp),
            )
            Text(verdict ?: "Starting…", style = MaterialTheme.typography.bodyMedium)
        }
    }

    NowPlayingCard(nowPlaying)

    // Controls always work: with song-info access via media sessions, otherwise via media buttons.
    val canControl = true
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { actions.command("prev") }, enabled = canControl, modifier = Modifier.weight(1f)) {
            Text("⏮")
        }
        Button(onClick = { actions.command("toggle") }, enabled = canControl, modifier = Modifier.weight(2f)) {
            Text(if (nowPlaying?.playing == true) "Pause" else "Play")
        }
        OutlinedButton(onClick = { actions.command("next") }, enabled = canControl, modifier = Modifier.weight(1f)) {
            Text("⏭")
        }
    }

    OutlinedButton(onClick = actions::stop, modifier = Modifier.fillMaxWidth()) {
        Text(
            when (role) {
                Role.HOST -> "Stop sharing"
                Role.LISTENER -> "Leave"
                else -> "Stop test"
            }
        )
    }
}

@Composable
private fun NowPlayingCard(np: NowPlaying?) {
    SectionCard("Now playing") {
        when {
            np != null && np.title == null && np.app == null ->
                Text(
                    if (np.playing) "♪ Music is playing" else "Paused",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            np != null -> {
                Text(np.title ?: "Unknown title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                np.artist?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    "${np.app ?: "Unknown app"} · ${if (np.playing) "playing" else "paused"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> Text("Nothing playing yet.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun connText(c: Conn) = when (c) {
    Conn.IDLE -> "Not connected"
    Conn.CONNECTING -> "Connecting…"
    Conn.CONNECTED -> "Connected"
    Conn.RECONNECTING -> "Reconnecting…"
}
