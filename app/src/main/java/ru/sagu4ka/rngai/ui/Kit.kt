package ru.sagu4ka.rngai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.sagu4ka.rngai.data.ProfileEntity
import ru.sagu4ka.rngai.ml.Wheel
import java.util.Locale

// --------------------------------------------------------------------- палитра
object C {
    val Bg0 = Color(0xFF0B0B12)
    val Bg1 = Color(0xFF14141D)
    val Bg2 = Color(0xFF1D1D2A)
    val Line = Color(0xFF2A2A3B)
    val Violet = Color(0xFF8B5CF6)
    val Blue = Color(0xFF3B82F6)
    val Cyan = Color(0xFF22D3EE)
    val Text = Color(0xFFEDEDF7)
    val Muted = Color(0xFF8E8EA8)
    val Good = Color(0xFF34D399)
    val Warn = Color(0xFFFBBF24)
    val Bad = Color(0xFFF87171)
    val Red = Color(0xFFC62F3A)
    val Black = Color(0xFF262633)
    val Green = Color(0xFF1F9D55)
    val Accent = Brush.linearGradient(listOf(Violet, Blue))
}

private val scheme = darkColorScheme(
    primary = C.Violet, onPrimary = Color.White,
    secondary = C.Blue, onSecondary = Color.White,
    background = C.Bg0, onBackground = C.Text,
    surface = C.Bg1, onSurface = C.Text,
    surfaceVariant = C.Bg2, onSurfaceVariant = C.Muted,
    outline = C.Line, error = C.Bad
)

@Composable
fun SagukaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

// ------------------------------------------------------------------ форматирование
private val ru = Locale.forLanguageTag("ru-RU")
fun fmt(v: Double, dec: Int = 1): String = String.format(ru, "%.${dec}f", v)
fun pct(p: Double, dec: Int = 1): String = fmt(p * 100, dec) + "%"
fun signed(v: Double, dec: Int = 1): String = (if (v >= 0) "+" else "−") + fmt(kotlin.math.abs(v), dec)

fun sectorColor(p: ProfileEntity, idx: Int): Color =
    if (p.sectorCount == 37 && p.sectorNames.isBlank()) when {
        idx == 0 -> C.Green
        idx in Wheel.RED -> C.Red
        else -> C.Black
    } else Color.hsv((idx * 360f / p.sectorCount) % 360f, 0.55f, 0.72f)

// --------------------------------------------------------------------- компоненты

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(C.Bg2, C.Bg1)))
            .border(BorderStroke(1.dp, if (glow) C.Accent else Brush.linearGradient(listOf(C.Line, C.Line))), shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun ScreenTitle(title: String, sub: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, color = C.Muted, fontSize = 13.sp)
        }
        trailing?.invoke()
    }
}

@Composable
fun Pill(text: String, color: Color = C.Violet, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun SectorBall(name: String, color: Color, size: Dp = 34.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1,
            fontSize = (size.value * if (name.length > 2) 0.30f else 0.42f).sp
        )
    }
}

/** Полоса вероятности с плавной анимацией. */
@Composable
fun ProbRow(
    rank: Int, name: String, color: Color, p: Double, maxP: Double, muted: Boolean = false, highlight: Boolean = false
) {
    val target = if (maxP <= 0) 0f else (p / maxP).toFloat().coerceIn(0f, 1f)
    val anim by animateFloatAsState(target, tween(650, easing = FastOutSlowInEasing), label = "bar")
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", color = C.Muted, fontSize = 12.sp, modifier = Modifier.width(20.dp))
        SectorBall(name, color, 32.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(50)).background(C.Bg0)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(anim.coerceAtLeast(0.02f)).clip(RoundedCornerShape(50))
                    .background(
                        if (muted) Brush.horizontalGradient(listOf(C.Line, C.Muted.copy(alpha = 0.6f)))
                        else Brush.horizontalGradient(listOf(C.Violet, C.Blue))
                    )
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            pct(p, 2), color = if (highlight) C.Text else if (muted) C.Muted else C.Text,
            fontSize = 14.sp, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.width(62.dp), textAlign = TextAlign.End
        )
    }
}

/** Кольцевой индикатор прогресса с подписью. */
@Composable
fun RingGauge(progress: Float, center: String, label: String, color: Brush = C.Accent, size: Dp = 96.dp) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).drawBehind {
                val sw = 10.dp.toPx()
                val inset = sw / 2
                val s = Size(this.size.width - sw, this.size.height - sw)
                drawArc(C.Line, 135f, 270f, false, Offset(inset, inset), s, style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(color, 135f, 270f * anim, false, Offset(inset, inset), s, style = Stroke(sw, cap = StrokeCap.Round))
            },
            contentAlignment = Alignment.Center
        ) { Text(center, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = C.Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun Banner(text: String, color: Color, title: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(12.dp)
    ) {
        Column {
            if (title != null) Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text, color = C.Text.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.Violet, contentColor = Color.White, disabledContainerColor = C.Line)
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, color: Color = C.Text, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, C.Line), colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) { Text(text, fontWeight = FontWeight.Medium) }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), color = C.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
fun Stat(label: String, value: String, color: Color = C.Text, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = C.Muted, fontSize = 11.sp)
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, brush: Brush = C.Accent) {
    val a by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "pl")
    Box(modifier.height(8.dp).clip(RoundedCornerShape(50)).background(C.Bg0)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(a.coerceAtLeast(0.01f)).clip(RoundedCornerShape(50)).background(brush))
    }
}

/** Тихая пульсация для главного сектора. */
@Composable
fun pulse(): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    return v
}

@Suppress("unused")
private val keepCorner = CornerRadius(0f)
