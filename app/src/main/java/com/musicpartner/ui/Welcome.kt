package com.musicpartner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Lite start flow: pick a mode → short loading screen → main screen. */
enum class StartStage { CHOOSE, LOADING, MAIN }

private val PrincessPink = Color(0xFFE75480)
private val PrincessPlum = Color(0xFF5C1236)
private val TechGreen = Color(0xFF00FF9C)
private val TechCyan = Color(0xFF00D1FF)
private val TechDim = Color(0xFF6E8B7E)

// ───────────────────────────── Loading screen ─────────────────────────────

/**
 * Short (~2 s) greeting after choosing a mode, then [onDone].
 * Each mode mentions the other one: Princess → "your techiee is waiting", Techie → "your princess is waiting".
 */
@Composable
fun CuteLoading(vibe: VibeId, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(vibe) {
        progress.animateTo(1f, tween(1800, easing = LinearEasing))
        delay(200)
        onDone()
    }
    if (vibe == VibeId.TECHIE) TechieLoading(progress.value) else PrincessLoading(progress.value)
}

@Composable
private fun PrincessLoading(progress: Float) {
    val pulse by rememberInfiniteTransition(label = "princess").animateFloat(
        1f, 1.12f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFE4F0), Color(0xFFF6E8FF), Color(0xFFFFF0F6)))),
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎀", Modifier.scale(pulse), fontSize = 80.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Hello Cutiee",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Cursive,
                color = PrincessPink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "your techiee is waiting for you",
                fontSize = 18.sp,
                color = PrincessPlum,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(0.7f).height(8.dp).clip(RoundedCornerShape(50)),
                color = PrincessPink,
                trackColor = Color(0xFFFFD6E7),
            )
        }
    }
}

@Composable
private fun TechieLoading(progress: Float) {
    val cursor by rememberInfiniteTransition(label = "cursor").animateFloat(
        1f, 0f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "blink",
    )
    val bars = 20
    val filled = (progress * bars).toInt()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF05080C), Color(0xFF07140F)))),
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("> syncing hearts ... [OK]", fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = TechDim)
            Spacer(Modifier.height(20.dp))
            Text(
                "Hello Cutiee",
                fontFamily = FontFamily.Monospace,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TechGreen,
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.Row {
                Text(
                    "your princess is waiting for you",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = TechCyan,
                )
                Text("▌", Modifier.alpha(if (cursor > 0.5f) 1f else 0f), fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = TechCyan)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "[" + "█".repeat(filled) + "░".repeat(bars - filled) + "] ${(progress * 100).toInt()}%",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = TechGreen,
            )
        }
    }
}
