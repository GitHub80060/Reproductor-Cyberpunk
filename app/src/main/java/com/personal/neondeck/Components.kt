package com.personal.neondeck

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

// ───────────────────────── Paleta y tipografía ─────────────────────────

/** Un tema de color: acento principal, acento secundario, color de alerta y fondos. */
class NeonTheme(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val alert: Color,
    val bg: Color,
    val panel: Color,
    val panel2: Color
)

val NeonThemes = listOf(
    NeonTheme("VOLT", Color(0xFFFCEE0A), Color(0xFF00F0FF), Color(0xFFFF003C), Color(0xFF07070B), Color(0xFF0F0F16), Color(0xFF171721)),
    NeonTheme("ROSA NEÓN", Color(0xFFFF2A6D), Color(0xFF05D9E8), Color(0xFFFFF200), Color(0xFF0A060C), Color(0xFF130E16), Color(0xFF1C1522)),
    NeonTheme("ÁCIDO", Color(0xFF39FF14), Color(0xFFFF00E5), Color(0xFFFF3B3B), Color(0xFF050A06), Color(0xFF0C140E), Color(0xFF131E16)),
    NeonTheme("ÁRTICO", Color(0xFF00F0FF), Color(0xFFB388FF), Color(0xFFFF4D6D), Color(0xFF05090D), Color(0xFF0C131A), Color(0xFF131C26)),
    NeonTheme("SOLAR", Color(0xFFFF8A00), Color(0xFF00D1FF), Color(0xFFFF003C), Color(0xFF0B0705), Color(0xFF16100C), Color(0xFF201812)),
    NeonTheme("VIOLETA", Color(0xFFB026FF), Color(0xFF00F0FF), Color(0xFFFF2A6D), Color(0xFF09060F), Color(0xFF110C1A), Color(0xFF1A1426))
)

/** Colores actuales de la app. Al cambiar [themeIndex] toda la interfaz se redibuja con la nueva paleta. */
object Cy {
    var themeIndex by mutableIntStateOf(0)

    private val t: NeonTheme get() = NeonThemes[themeIndex.coerceIn(0, NeonThemes.lastIndex)]

    val Bg: Color get() = t.bg
    val Panel: Color get() = t.panel
    val Panel2: Color get() = t.panel2
    val Primary: Color get() = t.primary
    val Secondary: Color get() = t.secondary
    val Alert: Color get() = t.alert
    val Dim = Color(0xFF7C7C92)
    val Text = Color(0xFFE9E9F2)
}

val Orbitron = FontFamily(Font(R.font.orbitron))

val Rajdhani = FontFamily(
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_bold, FontWeight.Bold)
)

fun tx(
    size: TextUnit,
    color: Color,
    family: FontFamily = Rajdhani,
    weight: FontWeight = FontWeight.Medium,
    spacing: TextUnit = 0.sp
) = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = family,
    fontWeight = weight,
    letterSpacing = spacing
)

// ───────────────────────── Fondo: rejilla + brillos + líneas de escaneo ─────────────────────────

fun Modifier.cyberBackground(): Modifier = this
    .background(Cy.Bg)
    .drawBehind {
        val step = 44.dp.toPx()
        val grid = Cy.Secondary.copy(alpha = 0.04f)
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawRect(
            Brush.radialGradient(
                colors = listOf(Cy.Alert.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width, 0f),
                radius = size.width * 1.1f
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(Cy.Secondary.copy(alpha = 0.09f), Color.Transparent),
                center = Offset(0f, size.height),
                radius = size.width * 1.1f
            )
        )
    }
    .drawWithContent {
        drawContent()
        val lineColor = Color.Black.copy(alpha = 0.16f)
        val stepY = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(lineColor, Offset(0f, y), Size(size.width, 1f))
            y += stepY
        }
    }

// ───────────────────────── Texto con efecto glitch ─────────────────────────

@Composable
fun GlitchText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "glitch")
    val shift = t.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable<Float>(
            animation = keyframes<Float> {
                durationMillis = 4200
                0f at 0
                0f at 3300
                1f at 3340
                0f at 3400
                -0.8f at 3480
                0.6f at 3530
                0f at 3620
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "shift"
    )
    Box(modifier) {
        Text(
            text = text,
            style = style.copy(color = Cy.Alert.copy(alpha = 0.85f)),
            modifier = Modifier.graphicsLayer { translationX = (1.3f + shift.value * 7f) * density }
        )
        Text(
            text = text,
            style = style.copy(color = Cy.Secondary.copy(alpha = 0.85f)),
            modifier = Modifier.graphicsLayer { translationX = -(1.3f + shift.value * 7f) * density }
        )
        Text(text = text, style = style)
    }
}

// ───────────────────────── Íconos dibujados a mano (angulares) ─────────────────────────

enum class Ico { PLAY, PAUSE, NEXT, PREV, SHUFFLE, REPEAT, REPEAT_ONE, LIB, DECK, EQ, SEARCH, REFRESH, USER }

private fun DrawScope.poly(color: Color, s: Float, vararg pts: Float) {
    val path = Path()
    var i = 0
    while (i < pts.size) {
        if (i == 0) path.moveTo(pts[i] * s, pts[i + 1] * s) else path.lineTo(pts[i] * s, pts[i + 1] * s)
        i += 2
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.seg(color: Color, s: Float, x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 2f) {
    drawLine(
        color = color,
        start = Offset(x1 * s, y1 * s),
        end = Offset(x2 * s, y2 * s),
        strokeWidth = w * s,
        cap = StrokeCap.Square
    )
}

@Composable
fun CyberIcon(ico: Ico, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        when (ico) {
            Ico.PLAY -> poly(color, s, 7f, 4f, 20f, 12f, 7f, 20f)
            Ico.PAUSE -> {
                poly(color, s, 6f, 4f, 10f, 4f, 10f, 20f, 6f, 20f)
                poly(color, s, 14f, 4f, 18f, 4f, 18f, 20f, 14f, 20f)
            }
            Ico.NEXT -> {
                poly(color, s, 5f, 5f, 16f, 12f, 5f, 19f)
                poly(color, s, 17f, 5f, 20f, 5f, 20f, 19f, 17f, 19f)
            }
            Ico.PREV -> {
                poly(color, s, 19f, 5f, 8f, 12f, 19f, 19f)
                poly(color, s, 4f, 5f, 7f, 5f, 7f, 19f, 4f, 19f)
            }
            Ico.SHUFFLE -> {
                seg(color, s, 3f, 7f, 8f, 7f)
                seg(color, s, 8f, 7f, 16f, 17f)
                seg(color, s, 16f, 17f, 18f, 17f)
                seg(color, s, 3f, 17f, 8f, 17f)
                seg(color, s, 8f, 17f, 16f, 7f)
                seg(color, s, 16f, 7f, 18f, 7f)
                poly(color, s, 18f, 4f, 22f, 7f, 18f, 10f)
                poly(color, s, 18f, 14f, 22f, 17f, 18f, 20f)
            }
            Ico.REPEAT, Ico.REPEAT_ONE -> {
                seg(color, s, 4f, 10f, 4f, 7f)
                seg(color, s, 4f, 7f, 16f, 7f)
                seg(color, s, 20f, 14f, 20f, 17f)
                seg(color, s, 20f, 17f, 8f, 17f)
                poly(color, s, 16f, 4f, 20f, 7f, 16f, 10f)
                poly(color, s, 8f, 14f, 4f, 17f, 8f, 20f)
                if (ico == Ico.REPEAT_ONE) seg(color, s, 12f, 9.5f, 12f, 14.5f, 1.8f)
            }
            Ico.LIB -> {
                seg(color, s, 4f, 6f, 20f, 6f)
                seg(color, s, 4f, 12f, 20f, 12f)
                seg(color, s, 4f, 18f, 13f, 18f)
            }
            Ico.DECK -> {
                drawCircle(color = color, radius = 9f * s, center = Offset(12f * s, 12f * s), style = Stroke(width = 2f * s))
                drawCircle(color = color, radius = 2.6f * s, center = Offset(12f * s, 12f * s))
            }
            Ico.EQ -> {
                seg(color, s, 6f, 3f, 6f, 21f, 1.6f)
                seg(color, s, 12f, 3f, 12f, 21f, 1.6f)
                seg(color, s, 18f, 3f, 18f, 21f, 1.6f)
                poly(color, s, 3.5f, 13f, 8.5f, 13f, 8.5f, 17f, 3.5f, 17f)
                poly(color, s, 9.5f, 6f, 14.5f, 6f, 14.5f, 10f, 9.5f, 10f)
                poly(color, s, 15.5f, 14f, 20.5f, 14f, 20.5f, 18f, 15.5f, 18f)
            }
            Ico.SEARCH -> {
                drawCircle(color = color, radius = 6f * s, center = Offset(10f * s, 10f * s), style = Stroke(width = 2f * s))
                seg(color, s, 14.5f, 14.5f, 20f, 20f)
            }
            Ico.USER -> {
                drawCircle(color = color, radius = 4.5f * s, center = Offset(12f * s, 8f * s))
                poly(color, s, 4f, 21f, 6f, 15f, 18f, 15f, 20f, 21f)
            }
            Ico.REFRESH -> {
                drawArc(
                    color = color,
                    startAngle = 40f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = Offset(4f * s, 4f * s),
                    size = Size(16f * s, 16f * s),
                    style = Stroke(width = 2f * s)
                )
                poly(color, s, 14f, 2f, 22f, 6f, 15f, 10f)
            }
        }
    }
}

// ───────────────────────── Botones y paneles ─────────────────────────

@Composable
fun CyberIconButton(
    ico: Ico,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = Cy.Primary,
    filled: Boolean = false,
    iconSize: Dp = 22.dp
) {
    val shape = CutCornerShape(topStart = size / 4, bottomEnd = size / 4)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (filled) color else Color.Transparent)
            .border(1.5.dp, color, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CyberIcon(ico, if (filled) Cy.Bg else color, Modifier.size(iconSize))
    }
}

@Composable
fun CyberTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Cy.Primary,
    filled: Boolean = false
) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    Box(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(if (filled) color else Color.Transparent)
            .border(1.5.dp, color, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = tx(14.sp, if (filled) Cy.Bg else color, Rajdhani, FontWeight.Bold, 1.5.sp),
            modifier = Modifier.padding(horizontal = 14.dp)
        )
    }
}

fun Modifier.cutPanel(
    border: Color = Cy.Primary.copy(alpha = 0.45f),
    fill: Color = Cy.Panel,
    cut: Dp = 14.dp
): Modifier {
    val shape = CutCornerShape(topStart = cut, bottomEnd = cut)
    return this
        .clip(shape)
        .background(fill)
        .border(1.dp, border, shape)
}

// ───────────────────────── Barra segmentada (progreso, EQ, volumen) ─────────────────────────

@Composable
fun NeonSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Cy.Primary,
    onFinish: () -> Unit = {},
    segments: Int = 36,
    height: Dp = 32.dp
) {
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)

    Canvas(
        modifier
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    change((offset.x / size.width).coerceIn(0f, 1f))
                    finish()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> change((offset.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() }
                ) { pointer, _ ->
                    pointer.consume()
                    change((pointer.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        val gap = 3.dp.toPx()
        val skew = 5.dp.toPx()
        val segW = (size.width - skew - gap * (segments - 1)) / segments
        val barH = size.height * 0.62f
        val top = (size.height - barH) / 2f
        val filled = (value.coerceIn(0f, 1f) * segments).toInt()
        for (i in 0 until segments) {
            val x = i * (segW + gap)
            val on = i < filled
            val c = when {
                on && i == filled - 1 -> Color.White
                on -> color
                else -> Cy.Dim.copy(alpha = 0.22f)
            }
            val path = Path().apply {
                moveTo(x + skew, top)
                lineTo(x + segW + skew, top)
                lineTo(x + segW, top + barH)
                lineTo(x, top + barH)
                close()
            }
            drawPath(path, c)
        }
    }
}

// ───────────────────────── Visualizador decorativo ─────────────────────────

/** Barras animadas de adorno. Se mueven mientras suena música; no leen el audio real. */
@Composable
fun SpectrumBars(playing: Boolean, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    val amp by animateFloatAsState(
        targetValue = if (playing) 1f else 0.08f,
        animationSpec = tween(500),
        label = "amp"
    )
    LaunchedEffect(playing) {
        while (playing) {
            androidx.compose.runtime.withFrameNanos { nanos ->
                time = (nanos % 1_000_000_000_000L) / 1_000_000_000f
            }
        }
    }
    Canvas(modifier.graphicsLayer { }) {
        val n = 28
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        val blockH = 5.dp.toPx()
        val blockGap = 2.dp.toPx()
        val maxBlocks = (size.height / (blockH + blockGap)).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val f = i / (n - 1f)
            val w1 = 0.5f + 0.5f * sin(time * (3.1f + (i % 5) * 0.9f) + i * 1.7f)
            val w2 = 0.5f + 0.5f * sin(time * 1.3f + i * 0.45f)
            val h = (0.1f + 0.9f * w1 * (0.4f + 0.6f * w2)) * (1f - 0.45f * f) * amp
            val blocks = (h * maxBlocks).toInt().coerceIn(1, maxBlocks)
            for (b in 0 until blocks) {
                val y = size.height - (b + 1) * (blockH + blockGap) + blockGap
                val c = when {
                    b >= maxBlocks * 0.8f -> Cy.Alert
                    b >= maxBlocks * 0.5f -> Cy.Primary
                    else -> Cy.Secondary
                }
                drawRect(c, Offset(i * (bw + gap), y), Size(bw, blockH))
            }
        }
    }
}

// ───────────────────────── Carátula vacía ─────────────────────────

@Composable
fun NoSignalArt(modifier: Modifier = Modifier) {
    Box(modifier.background(Cy.Panel2), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Cy.Primary.copy(alpha = 0.07f)
            var k = 0f
            while (k < size.width + size.height) {
                drawLine(c, Offset(k, 0f), Offset(k - size.height, size.height), 2.dp.toPx())
                k += 18.dp.toPx()
            }
        }
        GlitchText("NO SIGNAL", tx(22.sp, Cy.Primary, Orbitron, FontWeight.Bold, 2.sp))
    }
}
