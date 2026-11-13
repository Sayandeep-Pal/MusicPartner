package com.musicpartner.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Look-and-feel modes. Princess and Techie are Lite-only; Classic is the Full edition's plain look. */
enum class VibeId { CLASSIC, PRINCESS, TECHIE }

/** All user-facing wording, so each vibe can speak in its own voice. */
data class Copy(
    val appTitle: String,
    val tagline: String,
    val pickerTitle: String,
    val setupTitle: String,
    val liteNote: String,
    val serverLabel: String,
    val shareTitle: String,
    val shareBody: String,
    val startSharing: String,
    val joinTitle: String,
    val roomCodeLabel: String,
    val joinButton: String,
    val hostTitle: String,
    val roomCodeHeading: String,
    val sendCode: String,
    val copyCode: String,
    val copied: String,
    val statServer: String,
    val statFriends: String,
    val statUpload: String,
    val testTitle: String,
    val testBody: String,
    val listenerTitle: String,
    val statRoom: String,
    val hostAway: String,
    val statBuffer: String,
    val capturedTitle: String,
    val starting: String,
    val nowPlayingTitle: String,
    val musicPlaying: String,
    val paused: String,
    val nothingPlaying: String,
    val play: String,
    val pause: String,
    val prev: String,
    val next: String,
    val stopHost: String,
    val leave: String,
    val stopTest: String,
    val connIdle: String,
    val connConnecting: String,
    val connConnected: String,
    val connReconnecting: String,
    val ok: String,
)

data class Vibe(
    val id: VibeId,
    val colors: ColorScheme,
    val background: Brush,
    val cardColor: Color,
    val cardBorder: Color?,
    val cardBorderWidth: Dp,
    val corner: Dp,
    val titleFont: FontFamily,
    val bodyFont: FontFamily,
    val copy: Copy,
)

val LocalVibe = staticCompositionLocalOf { Vibes.classic(dark = false) }

object Vibes {
    fun of(id: VibeId, dark: Boolean): Vibe = when (id) {
        VibeId.CLASSIC -> classic(dark)
        VibeId.PRINCESS -> princess
        VibeId.TECHIE -> techie
    }

    fun classic(dark: Boolean): Vibe {
        val colors = if (dark) darkColorScheme() else lightColorScheme()
        return Vibe(
            id = VibeId.CLASSIC,
            colors = colors,
            background = Brush.verticalGradient(listOf(colors.background, colors.background)),
            cardColor = colors.surfaceContainerHighest,
            cardBorder = null,
            cardBorderWidth = 0.dp,
            corner = 12.dp,
            titleFont = FontFamily.Default,
            bodyFont = FontFamily.Default,
            copy = Copy(
                appTitle = "MusicPartner",
                tagline = "Listen to the same song at the same time, from any music app.",
                pickerTitle = "Look",
                setupTitle = "Setup",
                liteNote = "Lite edition: play, pause and skip work from both phones. Song names aren't shown.",
                serverLabel = "Server address",
                shareTitle = "Share my music",
                shareBody = "Play a song in YouTube Music, Spotify, YouTube or any app. Your friend hears it with you.",
                startSharing = "Start sharing",
                joinTitle = "Join a friend",
                roomCodeLabel = "Room code",
                joinButton = "Join",
                hostTitle = "Sharing your music",
                roomCodeHeading = "Room code",
                sendCode = "Send this code to your friend.",
                copyCode = "Copy",
                copied = "Copied ✓",
                statServer = "Server",
                statFriends = "Friends listening",
                statUpload = "Upload",
                testTitle = "Capture test",
                testBody = "Play music in the app you want to test, then come back here. " +
                    "If the bar moves, that app works with MusicPartner.",
                listenerTitle = "Listening with a friend",
                statRoom = "Room",
                hostAway = "Your friend's connection dropped. Waiting for them to come back…",
                statBuffer = "Buffer",
                capturedTitle = "Captured audio",
                starting = "Starting…",
                nowPlayingTitle = "Now playing",
                musicPlaying = "♪ Music is playing",
                paused = "Paused",
                nothingPlaying = "Nothing playing yet.",
                play = "Play",
                pause = "Pause",
                prev = "⏮",
                next = "⏭",
                stopHost = "Stop sharing",
                leave = "Leave",
                stopTest = "Stop test",
                connIdle = "Not connected",
                connConnecting = "Connecting…",
                connConnected = "Connected",
                connReconnecting = "Reconnecting…",
                ok = "OK",
            ),
        )
    }

    // 🎀 Princess: pastel pinks and lavender, soft round corners, cursive titles, sparkles.
    val princess: Vibe = run {
        val colors = lightColorScheme(
            primary = Color(0xFFE75480),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFD6E7),
            onPrimaryContainer = Color(0xFF5C1236),
            secondary = Color(0xFFB57EDC),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF3E5FF),
            onSecondaryContainer = Color(0xFF4B2366),
            tertiary = Color(0xFFFFB347),
            background = Color(0xFFFFF0F6),
            onBackground = Color(0xFF4A1D3A),
            surface = Color(0xFFFFF7FB),
            onSurface = Color(0xFF4A1D3A),
            surfaceVariant = Color(0xFFFCE4EF),
            onSurfaceVariant = Color(0xFF8E4F74),
            surfaceContainerHighest = Color(0xFFFFE3EF),
            outline = Color(0xFFF5A3C7),
            error = Color(0xFFD81B60),
        )
        Vibe(
            id = VibeId.PRINCESS,
            colors = colors,
            background = Brush.verticalGradient(
                listOf(Color(0xFFFFE4F0), Color(0xFFF6E8FF), Color(0xFFFFF0F6)),
            ),
            cardColor = Color(0xFFFFFFFF).copy(alpha = 0.85f),
            cardBorder = Color(0xFFF5A3C7),
            cardBorderWidth = 1.5.dp,
            corner = 28.dp,
            titleFont = FontFamily.Cursive,
            bodyFont = FontFamily.SansSerif,
            copy = Copy(
                appTitle = "In Sync With You 🎀",
                tagline = "✨ two hearts, one beat · every song with your techiee, at the very same moment ✨",
                pickerTitle = "Choose your vibe ✨",
                setupTitle = "👑 Castle settings",
                liteNote = "Play, pause and skip work from both phones 💕 Song names stay a royal secret.",
                serverLabel = "Castle address",
                shareTitle = "🎀 Be the pilot of your techiee",
                shareBody = "Play a song in any app and your techiee hears it with you 💖",
                startSharing = "✨ Start the party",
                joinTitle = "💌 Join your techiee",
                roomCodeLabel = "Secret code",
                joinButton = "💖 Join the party",
                hostTitle = "👑 You're piloting the music",
                roomCodeHeading = "Your secret code 🎀",
                sendCode = "Send this code to your techiee 💌",
                copyCode = "Copy 🎀",
                copied = "Copied 💖",
                statServer = "Castle",
                statFriends = "Cutiees listening",
                statUpload = "Sparkle speed",
                testTitle = "🪄 Magic test",
                testBody = "Play music in your favourite app, then come back here. " +
                    "If the sparkles move, the magic works ✨",
                listenerTitle = "🎀 In sync with your techiee",
                statRoom = "Secret code",
                hostAway = "Your techiee's connection slipped away 🥺 Waiting for them to come back…",
                statBuffer = "Cushion",
                capturedTitle = "✨ Sparkle meter",
                starting = "Sprinkling fairy dust…",
                nowPlayingTitle = "🎶 Now playing",
                musicPlaying = "🎀 Music is playing",
                paused = "💤 Paused",
                nothingPlaying = "No song yet, princess 🌸",
                play = "▶ Play 💖",
                pause = "⏸ Pause",
                prev = "⏮",
                next = "⏭",
                stopHost = "🌙 End the party",
                leave = "👋 Leave the party",
                stopTest = "Stop the magic",
                connIdle = "Not connected",
                connConnecting = "Opening the castle gates…",
                connConnected = "In sync 💞",
                connReconnecting = "Finding the way back…",
                ok = "Okay 💕",
            ),
        )
    }

    // ⌨ Techie: terminal black, neon green and cyan, sharp corners, monospace everywhere.
    val techie: Vibe = run {
        val colors = darkColorScheme(
            primary = Color(0xFF00FF9C),
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF003D26),
            onPrimaryContainer = Color(0xFF00FF9C),
            secondary = Color(0xFF00D1FF),
            onSecondary = Color.Black,
            secondaryContainer = Color(0xFF062A33),
            onSecondaryContainer = Color(0xFF7FE9FF),
            tertiary = Color(0xFFFFD60A),
            background = Color(0xFF05080C),
            onBackground = Color(0xFFC9F7E3),
            surface = Color(0xFF0A0F14),
            onSurface = Color(0xFFC9F7E3),
            surfaceVariant = Color(0xFF101820),
            onSurfaceVariant = Color(0xFF6E8B7E),
            surfaceContainerHighest = Color(0xFF13202A),
            outline = Color(0xFF1F5A40),
            error = Color(0xFFFF4D6D),
        )
        Vibe(
            id = VibeId.TECHIE,
            colors = colors,
            background = Brush.verticalGradient(listOf(Color(0xFF05080C), Color(0xFF07140F))),
            cardColor = Color(0xFF0B1117),
            cardBorder = Color(0xFF1F5A40),
            cardBorderWidth = 1.dp,
            corner = 3.dp,
            titleFont = FontFamily.Monospace,
            bodyFont = FontFamily.Monospace,
            copy = Copy(
                appTitle = "> in_sync_with_you",
                tagline = "// two devices. one wavelength. zero drift.",
                pickerTitle = "// theme",
                setupTitle = "[ config ]",
                liteNote = "build=lite · transport_ctl=ok · metadata=null",
                serverLabel = "relay_url",
                shareTitle = "[ broadcast ]",
                shareBody = "Play audio in any app → piped to your peer in realtime.",
                startSharing = "$ ./share --start",
                joinTitle = "[ connect ]",
                roomCodeLabel = "room_id",
                joinButton = "$ ./join",
                hostTitle = "[ broadcasting ]",
                roomCodeHeading = "ROOM_ID",
                sendCode = "// transmit this id to your peer",
                copyCode = "$ copy",
                copied = "[copied]",
                statServer = "relay",
                statFriends = "peers",
                statUpload = "uplink",
                testTitle = "[ capture_test ]",
                testBody = "// start playback in the target app, then return. meter moving = capture OK.",
                listenerTitle = "[ in sync with princess ]",
                statRoom = "room_id",
                hostAway = "WARN: host link down. awaiting reconnect…",
                statBuffer = "jitter_buf",
                capturedTitle = "[ signal ]",
                starting = "initializing…",
                nowPlayingTitle = "[ now_playing ]",
                musicPlaying = "▶ stream active",
                paused = "❚❚ paused",
                nothingPlaying = "null // no stream yet",
                play = "▶ RUN",
                pause = "❚❚ HALT",
                prev = "|◀",
                next = "▶|",
                stopHost = "$ kill -9 share",
                leave = "$ disconnect",
                stopTest = "$ kill test",
                connIdle = "OFFLINE",
                connConnecting = "HANDSHAKE…",
                connConnected = "SYNCED ●",
                connReconnecting = "RETRYING…",
                ok = "ACK",
            ),
        )
    }
}

private fun Typography.withFonts(title: FontFamily, body: FontFamily): Typography {
    fun TextStyle.f(ff: FontFamily) = copy(fontFamily = ff)
    return copy(
        displayLarge = displayLarge.f(title), displayMedium = displayMedium.f(title), displaySmall = displaySmall.f(title),
        headlineLarge = headlineLarge.f(title), headlineMedium = headlineMedium.f(title), headlineSmall = headlineSmall.f(title),
        titleLarge = titleLarge.f(title), titleMedium = titleMedium.f(title), titleSmall = titleSmall.f(body),
        bodyLarge = bodyLarge.f(body), bodyMedium = bodyMedium.f(body), bodySmall = bodySmall.f(body),
        labelLarge = labelLarge.f(body), labelMedium = labelMedium.f(body), labelSmall = labelSmall.f(body),
    )
}

/** Applies a vibe's colors, fonts and shapes, and paints its background behind [content]. */
@Composable
fun VibeTheme(vibe: Vibe, content: @Composable () -> Unit) {
    val c = vibe.corner
    MaterialTheme(
        colorScheme = vibe.colors,
        typography = Typography().withFonts(vibe.titleFont, vibe.bodyFont),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(minOf(c, 8.dp)),
            small = RoundedCornerShape(minOf(c, 16.dp)),
            medium = RoundedCornerShape(c),
            large = RoundedCornerShape(c),
            extraLarge = RoundedCornerShape(c),
        ),
    ) {
        CompositionLocalProvider(LocalVibe provides vibe) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().drawBehind { drawRect(vibe.background) },
            ) { content() }
        }
    }
}
