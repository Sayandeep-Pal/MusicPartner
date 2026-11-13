package com.musicpartner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private val Pink = Color(0xFFE75480)
private val Plum = Color(0xFF5C1236)
private val Neon = Color(0xFF00FF9C)
private val Cyan = Color(0xFF00D1FF)
private val Dim = Color(0xFF6E8B7E)

private const val TWO_PI = (2 * PI).toFloat()

/**
 * Start-page mode chooser: two "worlds" stacked on one screen, meeting at an animated wave.
 * Top = Princess (pastel gradient, twinkling sparkles). Bottom = Techie (neon grid, data rain).
 * Tapping a world makes it sweep over the whole screen, then [onPick] is called.
 */
@Composable
fun ModeChooser(onPick: (VibeId) -> Unit) {
    // Fraction of the screen height owned by the Techie world (bottom).
    val split = remember { Animatable(0.5f) }
    var picked by remember { mutableStateOf<VibeId?>(null) }
    val scope = rememberCoroutineScope()

    fun choose(id: VibeId) {
        if (picked != null) return
        picked = id
        scope.launch {
            split.animateTo(if (id == VibeId.TECHIE) 1f else 0f, tween(700, easing = FastOutSlowInEasing))
            onPick(id)
        }
    }

    // One slow 12 s clock drives every ambient animation (integer multiples keep loops seamless).
    val t by rememberInfiniteTransition(label = "ambient").animateFloat(
        0f, 1f, infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart), label = "clock",
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val f = split.value
        val waveH = 28.dp
        val noRipple = remember { MutableInteractionSource() }

        // ── Princess world (fills the screen behind everything) ──
        PrincessBackdrop(t, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxWidth()
                .height(h * (1f - f))
                .clipToBounds()
                .clickable(interactionSource = noRipple, indication = null) { choose(VibeId.PRINCESS) },
            contentAlignment = Alignment.Center,
        ) {
            PrincessLabel(t, Modifier.statusBarsPadding().alpha(((1f - f) / 0.5f).coerceIn(0f, 1f)))
        }

        // ── Techie world (bottom panel with a wavy, glowing top edge) ──
        val wave = WaveTopShape(waveH, phase = t * 2f)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(h * f + waveH)
                .clip(wave)
                .clickable(interactionSource = noRipple, indication = null) { choose(VibeId.TECHIE) }
                .drawWithContent {
                    drawContent()
                    // Neon seam along the wave.
                    val p = wave.path(size, waveH.toPx(), t * 2f)
                    drawPath(p, Neon.copy(alpha = 0.35f), style = Stroke(width = 10.dp.toPx()))
                    drawPath(p, Neon, style = Stroke(width = 2.5.dp.toPx()))
                },
        ) {
            TechieBackdrop(t, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().padding(top = waveH),
                contentAlignment = Alignment.Center,
            ) {
                TechieLabel(t, Modifier.navigationBarsPadding().alpha((f / 0.5f).coerceIn(0f, 1f)))
            }
        }

        // ── Badge sitting on the seam ──
        if (picked == null) {
            ChooseBadge(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = h * (1f - f) - waveH / 2 - 18.dp),
            )
        }
    }
}

// ───────────────────────────── Princess world ─────────────────────────────

private class Sparkle(val x: Float, val y: Float, val r: Float, val phase: Float, val white: Boolean)
private class Bokeh(val x: Float, val y: Float, val r: Float, val phase: Float)

@Composable
private fun PrincessBackdrop(t: Float, modifier: Modifier) {
    val sparkles = remember {
        val rnd = Random(7)
        List(34) { Sparkle(rnd.nextFloat(), rnd.nextFloat(), 3f + rnd.nextFloat() * 7f, rnd.nextFloat(), rnd.nextBoolean()) }
    }
    val bokeh = remember {
        val rnd = Random(11)
        List(7) { Bokeh(rnd.nextFloat(), rnd.nextFloat() * 0.6f, 50f + rnd.nextFloat() * 90f, rnd.nextFloat()) }
    }
    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFFFFD1E6), Color(0xFFFBE3F1), Color(0xFFEBDDFF), Color(0xFFF8D7EE)),
            ),
        )
        val d = density
        // Soft blurred circles drifting side to side.
        bokeh.forEach { b ->
            val cx = (b.x + 0.05f * sin(TWO_PI * (t + b.phase))) * size.width
            val cy = b.y * size.height
            val r = b.r * d
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.55f), Pink.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        // Twinkling 4-point sparkles rising slowly.
        sparkles.forEach { s ->
            val y = ((s.y - t) % 1f + 1f) % 1f
            val tw = 0.5f + 0.5f * sin(TWO_PI * (t * 6f + s.phase))
            val r = s.r * d * (0.35f + 0.65f * tw)
            val c = Offset(s.x * size.width, y * size.height)
            val color = (if (s.white) Color.White else Color(0xFFFF8FBF)).copy(alpha = 0.35f + 0.6f * tw)
            drawPath(starPath(c, r), color)
        }
    }
}

private fun starPath(c: Offset, r: Float): Path = Path().apply {
    val k = r * 0.22f
    moveTo(c.x, c.y - r)
    lineTo(c.x + k, c.y - k)
    lineTo(c.x + r, c.y)
    lineTo(c.x + k, c.y + k)
    lineTo(c.x, c.y + r)
    lineTo(c.x - k, c.y + k)
    lineTo(c.x - r, c.y)
    lineTo(c.x - k, c.y - k)
    close()
}

@Composable
private fun PrincessLabel(t: Float, modifier: Modifier) {
    val bob = sin(TWO_PI * t * 4f) * 6f
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎀", Modifier.offset(y = bob.dp), fontSize = 64.sp)
        Text(
            "Princess",
            style = TextStyle(
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Bold,
                fontSize = 60.sp,
                color = Pink,
                shadow = Shadow(Color(0x88FF7EB3), Offset(0f, 4f), 24f),
            ),
        )
        Text("soft  ·  pink  ·  sparkly", color = Plum, fontSize = 15.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            "tap to enter ✨",
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.65f))
                .border(1.dp, Color(0xFFF5A3C7), RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 8.dp),
            color = Plum,
            fontSize = 13.sp,
        )
    }
}

// ───────────────────────────── Techie world ─────────────────────────────

private class RainCol(val x: Float, val speed: Int, val offset: Float, val len: Int)

@Composable
private fun TechieBackdrop(t: Float, modifier: Modifier) {
    val cols = remember {
        val rnd = Random(3)
        List(18) { RainCol((it + 0.5f) / 18f, 1 + rnd.nextInt(4), rnd.nextFloat(), 6 + rnd.nextInt(8)) }
    }
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF02060A), Color(0xFF04120C), Color(0xFF020805))))
        val w = size.width
        val hgt = size.height
        val d = density

        // Synthwave-style perspective grid in the lower part, scrolling toward the viewer.
        val horizon = hgt * 0.45f
        val vanish = Offset(w / 2f, horizon)
        val grid = Neon.copy(alpha = 0.16f)
        for (i in -10..10) {
            drawLine(grid, vanish, Offset(w / 2f + i * w * 0.16f, hgt), strokeWidth = 1f * d)
        }
        val rows = 9
        for (i in 0 until rows) {
            val z = ((i + (t * 8f) % 1f) / rows)
            val y = horizon + (hgt - horizon) * z * z
            drawLine(grid.copy(alpha = 0.06f + 0.22f * z), Offset(0f, y), Offset(w, y), strokeWidth = 1f * d)
        }
        // Horizon glow.
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Neon.copy(alpha = 0.10f), Color.Transparent),
                startY = horizon - 40 * d,
                endY = horizon + 40 * d,
            ),
            topLeft = Offset(0f, horizon - 40 * d),
            size = Size(w, 80 * d),
        )

        // Data rain: short glowing bits falling with fading trails.
        val bitW = 3f * d
        val bitH = 8f * d
        val gap = 12f * d
        cols.forEach { c ->
            val head = (((t * c.speed) + c.offset) % 1f) * (hgt + c.len * gap) - 20 * d
            for (k in 0 until c.len) {
                val y = head - k * gap
                if (y < -bitH || y > hgt) continue
                val a = (1f - k.toFloat() / c.len)
                val col = if (k == 0) Color(0xFFCCFFF0) else Neon
                drawRoundRect(
                    col.copy(alpha = 0.55f * a),
                    topLeft = Offset(c.x * w - bitW / 2, y),
                    size = Size(bitW, bitH),
                    cornerRadius = CornerRadius(bitW / 2),
                )
            }
        }
    }
}

@Composable
private fun TechieLabel(t: Float, modifier: Modifier) {
    val cursorOn = sin(TWO_PI * t * 12f) > 0f
    val glow = Shadow(Neon.copy(alpha = 0.8f), Offset.Zero, 28f)
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "</>",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 34.sp,
                color = Cyan,
                shadow = Shadow(Cyan.copy(alpha = 0.8f), Offset.Zero, 24f),
            ),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "> techie",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    color = Neon,
                    shadow = glow,
                ),
            )
            Text(
                "_",
                Modifier.alpha(if (cursorOn) 1f else 0f),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = Neon, shadow = glow),
            )
        }
        Text("// dark · neon · nerdy", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            "[ tap to enter ]",
            Modifier
                .border(1.dp, Neon.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                .background(Neon.copy(alpha = 0.06f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = Neon,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }
}

// ───────────────────────────── Seam badge & wave ─────────────────────────────

@Composable
private fun ChooseBadge(modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF120C16))
            .border(1.5.dp, Brush.horizontalGradient(listOf(Pink, Color(0xFFB57EDC), Neon)), RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text("✦", color = Color(0xFFFF8FBF), fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Text("choose your vibe", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(Modifier.width(10.dp))
        Text("⟩", color = Neon, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Rectangle whose top edge is a gentle sine wave of height [amp]. */
private class WaveTopShape(private val amp: Dp, private val phase: Float) : Shape {
    fun path(size: Size, ampPx: Float, phase: Float): Path = Path().apply {
        val w = size.width
        val step = w / 48f
        var x = 0f
        moveTo(0f, waveY(0f, w, ampPx, phase))
        while (x <= w) {
            lineTo(x, waveY(x, w, ampPx, phase))
            x += step
        }
        lineTo(w, waveY(w, w, ampPx, phase))
    }

    private fun waveY(x: Float, w: Float, a: Float, phase: Float) =
        a / 2f + sin(TWO_PI * (x / w * 1.25f + phase)) * a / 2f

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = path(size, with(density) { amp.toPx() }, phase).apply {
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(p)
    }
}
