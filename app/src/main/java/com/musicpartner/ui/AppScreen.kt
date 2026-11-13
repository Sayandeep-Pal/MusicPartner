package com.musicpartner.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicpartner.BuildConfig
import com.musicpartner.Conn
import com.musicpartner.NowPlaying
import com.musicpartner.Prefs
import com.musicpartner.Role
import com.musicpartner.Session
import kotlin.math.sqrt

interface Actions {
    fun openNotificationAccess()
    fun saveServerUrl(url: String)
    fun startHost()
    fun join(code: String)
    fun command(action: String)
    fun stop()
    fun setVibe(id: VibeId)
}

@Composable
fun AppScreen(notifAccess: Boolean, initialServerUrl: String, actions: Actions) {
    val role by Session.role.collectAsState()
    val message by Session.message.collectAsState()
    val vibe = LocalVibe.current

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(vibe)

        message?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { Session.message.value = null }) { Text(vibe.copy.ok) }
                }
            }
        }

        if (role == Role.NONE) {
            Home(notifAccess, initialServerUrl, actions)
        } else {
            ActiveSession(role, actions)
        }

        if (vibe.id == VibeId.PRINCESS) {
            Text(
                "🎀 made with love for princess 🎀",
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Header(vibe: Vibe) {
    val c = vibe.copy
    when (vibe.id) {
        VibeId.PRINCESS -> {
            Text("👑 ✨ 🎀 ✨ 👑", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 20.sp)
            Text(
                c.appTitle,
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                c.tagline,
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VibeId.TECHIE -> {
            // Blinking terminal cursor after the title.
            val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
                label = "cursorAlpha",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.appTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "▌",
                    Modifier.alpha(if (blink > 0.5f) 1f else 0f),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(c.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VibeId.CLASSIC -> {
            Text(c.appTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(c.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Home(notifAccess: Boolean, initialServerUrl: String, actions: Actions) {
    val c = LocalVibe.current.copy
    var server by remember { mutableStateOf(initialServerUrl) }
    var code by remember { mutableStateOf("") }

    SectionCard(c.setupTitle) {
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
            Text(c.liteNote, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        OutlinedTextField(
            value = server,
            onValueChange = {
                server = it
                actions.saveServerUrl(it)
            },
            label = { Text(c.serverLabel) },
            placeholder = { Text(Prefs.DEFAULT_SERVER) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SectionCard(c.shareTitle) {
        Text(c.shareBody, style = MaterialTheme.typography.bodySmall)
        Button(onClick = actions::startHost, modifier = Modifier.fillMaxWidth()) { Text(c.startSharing) }
    }

    SectionCard(c.joinTitle) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
            label = { Text(c.roomCodeLabel) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { actions.join(code) }, modifier = Modifier.fillMaxWidth()) { Text(c.joinButton) }
    }
}

@Composable
private fun ActiveSession(role: Role, actions: Actions) {
    val c = LocalVibe.current.copy
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
        Role.HOST -> SectionCard(c.hostTitle) {
            Text(c.roomCodeHeading, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    roomCode ?: "······",
                    Modifier.weight(1f),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 6.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                roomCode?.let { CopyCodeButton(it) }
            }
            Text(c.sendCode, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            StatusLine(c.statServer, connText(c, conn))
            StatusLine(c.statFriends, listeners.toString())
            StatusLine(c.statUpload, "$kbps kbps")
        }
        Role.TEST -> SectionCard(c.testTitle) {
            Text(c.testBody, style = MaterialTheme.typography.bodySmall)
        }
        Role.LISTENER -> SectionCard(c.listenerTitle) {
            StatusLine(c.statRoom, roomCode ?: "")
            StatusLine(c.statServer, connText(c, conn))
            if (hostAway) {
                Text(c.hostAway, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            StatusLine(c.statBuffer, "$bufferMs ms")
        }
        Role.NONE -> Unit
    }

    if (role == Role.HOST || role == Role.TEST) {
        SectionCard(c.capturedTitle) {
            LinearProgressIndicator(
                progress = { sqrt(level.coerceIn(0f, 1f)) },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
            Text(verdict ?: c.starting, style = MaterialTheme.typography.bodyMedium)
        }
    }

    NowPlayingCard(nowPlaying)

    // Controls always work: with song-info access via media sessions, otherwise via media buttons.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { actions.command("prev") }, modifier = Modifier.weight(1f)) { Text(c.prev) }
        Button(onClick = { actions.command("toggle") }, modifier = Modifier.weight(2f)) {
            Text(if (nowPlaying?.playing == true) c.pause else c.play)
        }
        OutlinedButton(onClick = { actions.command("next") }, modifier = Modifier.weight(1f)) { Text(c.next) }
    }

    OutlinedButton(onClick = actions::stop, modifier = Modifier.fillMaxWidth()) {
        Text(
            when (role) {
                Role.HOST -> c.stopHost
                Role.LISTENER -> c.leave
                else -> c.stopTest
            }
        )
    }
}

@Composable
private fun CopyCodeButton(code: String) {
    val c = LocalVibe.current.copy
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }
    OutlinedButton(onClick = {
        clipboard.setText(AnnotatedString(code))
        copied = true
    }) {
        Text(if (copied) c.copied else c.copyCode)
    }
}

@Composable
private fun NowPlayingCard(np: NowPlaying?) {
    val c = LocalVibe.current.copy
    SectionCard(c.nowPlayingTitle) {
        when {
            np != null && np.title == null && np.app == null ->
                Text(
                    if (np.playing) c.musicPlaying else c.paused,
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
            else -> Text(c.nothingPlaying, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val vibe = LocalVibe.current
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = vibe.cardColor, contentColor = MaterialTheme.colorScheme.onSurface),
        border = vibe.cardBorder?.let { BorderStroke(vibe.cardBorderWidth, it) },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (vibe.id == VibeId.CLASSIC) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
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

private fun connText(c: Copy, conn: Conn) = when (conn) {
    Conn.IDLE -> c.connIdle
    Conn.CONNECTING -> c.connConnecting
    Conn.CONNECTED -> c.connConnected
    Conn.RECONNECTING -> c.connReconnecting
}
