package ru.sagu4ka.rngai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.sagu4ka.rngai.data.ProfileEntity
import ru.sagu4ka.rngai.ml.CalibBin
import kotlin.math.max
import kotlin.math.min

/**
 * Кольцо-«колесо»: сегменты в порядке карманов. Цвет — насколько сектор выпадал чаще (фиолет)
 * или реже (синий) ожидаемого. Яркость растёт с отклонением.
 */
@Composable
fun WheelHeat(profile: ProfileEntity, counts: IntArray, order: IntArray, modifier: Modifier = Modifier) {
    val k = counts.size
    val n = counts.sum()
    val expected = if (n == 0) 0.0 else n.toDouble() / k
    val sd = kotlin.math.sqrt(max(1e-9, n * (1.0 / k) * (1 - 1.0 / k)))
    Canvas(modifier.aspectRatio(1f)) {
        val sw = size.minDimension * 0.17f
        val inset = sw / 2 + 4.dp.toPx()
        val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
        val sweep = 360f / k
        for (i in 0 until k) {
            val z = if (n == 0) 0.0 else (counts[order[i]] - expected) / sd
            val a = (0.18 + min(1.0, kotlin.math.abs(z) / 3.0) * 0.82).toFloat()
            val col = if (z >= 0) C.Violet.copy(alpha = a) else C.Blue.copy(alpha = a * 0.8f)
            drawArc(col, -90f + i * sweep + 0.6f, sweep - 1.2f, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Butt))
        }
    }
}

/** Линия накопленного ln-капитала с порогом значимости. */
@Composable
fun WealthChart(values: List<Double>, threshold: Double, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        if (values.size < 2) return@Canvas
        val lo = min(values.min(), -1.0)
        val hi = max(values.max(), threshold * 1.15)
        fun y(v: Double) = (size.height - ((v - lo) / (hi - lo)).toFloat() * size.height)
        val step = size.width / (values.size - 1)
        // нулевая линия и порог
        drawLine(C.Line, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1.dp.toPx())
        drawLine(C.Warn.copy(alpha = 0.7f), Offset(0f, y(threshold)), Offset(size.width, y(threshold)), 1.5.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * step, y(v)) }
        drawPath(path, Brush.horizontalGradient(listOf(C.Violet, C.Blue)), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        val fill = Path().apply { addPath(path); lineTo(size.width, y(0.0)); lineTo(0f, y(0.0)); close() }
        drawPath(fill, Brush.verticalGradient(listOf(C.Violet.copy(alpha = 0.22f), Color.Transparent)))
    }
}

/** Частота по секторам в процентах от ожидаемого (линия = норма). */
@Composable
fun FreqBars(counts: IntArray, profile: ProfileEntity, modifier: Modifier = Modifier) {
    val n = counts.sum()
    val k = counts.size
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (n == 0) return@Canvas
        val expected = n.toDouble() / k
        val top = max(counts.max().toDouble(), expected * 1.5)
        val bw = size.width / k
        counts.forEachIndexed { i, c ->
            val h = (c / top * size.height).toFloat()
            drawRect(sectorColor(profile, i).copy(alpha = 0.9f), Offset(i * bw + bw * 0.12f, size.height - h), Size(bw * 0.76f, h))
        }
        val ey = (size.height - expected / top * size.height).toFloat()
        drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, ey), Offset(size.width, ey), 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    }
}

/** Калибровка: по бинам — предсказанная и фактическая частота (в долях от 1/K). */
@Composable
fun CalibrationChart(bins: List<CalibBin>, k: Int, modifier: Modifier = Modifier) {
    val used = bins.filter { it.count >= 30 }
    if (used.isEmpty()) {
        Text("Для калибровки пока мало данных", color = C.Muted, fontSize = 12.sp)
        return
    }
    val maxV = max(used.maxOf { it.meanP * k }, used.maxOf { it.observed * k }).coerceAtLeast(1.2)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val bw = size.width / used.size
            used.forEachIndexed { i, b ->
                val h1 = (b.meanP * k / maxV * size.height).toFloat()
                val h2 = (b.observed * k / maxV * size.height).toFloat()
                drawRect(C.Violet, Offset(i * bw + bw * 0.12f, size.height - h1), Size(bw * 0.35f, h1))
                drawRect(C.Cyan, Offset(i * bw + bw * 0.52f, size.height - h2), Size(bw * 0.35f, h2))
            }
            val ny = (size.height - 1.0 / maxV * size.height).toFloat()
            drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, ny), Offset(size.width, ny), 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            used.forEach { Text(it.label, color = C.Muted, fontSize = 9.sp, modifier = Modifier.weight(1f)) }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).let { it }) { Canvas(Modifier.fillMaxSize()) { drawRect(C.Violet) } }
            Text("  прогноз     ", color = C.Muted, fontSize = 11.sp)
            Box(Modifier.size(8.dp)) { Canvas(Modifier.fillMaxSize()) { drawRect(C.Cyan) } }
            Text("  факт     белая линия = 1/K", color = C.Muted, fontSize = 11.sp, fontWeight = FontWeight.Normal)
        }
    }
}
