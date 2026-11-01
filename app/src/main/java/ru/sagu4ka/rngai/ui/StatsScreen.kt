package ru.sagu4ka.rngai.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.sagu4ka.rngai.Analysis
import ru.sagu4ka.rngai.ml.Wheel
import kotlin.math.abs

@Composable
fun StatsScreen(a: Analysis) {
    val p = a.profile
    val k = p.sectorCount
    val counts = IntArray(k).also { arr -> a.spins.forEach { arr[it.sector]++ } }
    val order = Wheel.order(k)
    var sortMode by remember { mutableIntStateOf(0) }    // 0 — по номеру, 1 — по частоте, 2 — по отклонению
    val rows = when (sortMode) {
        1 -> a.sectorStats.sortedByDescending { it.total }
        2 -> a.sectorStats.sortedByDescending { abs(it.z) }
        else -> a.sectorStats
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        item { ScreenTitle("Статистика", "Всего результатов: ${a.spins.size}") }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Карта отклонений по колесу", color = C.Text, fontWeight = FontWeight.SemiBold)
                Text("Фиолетовый — выпадало чаще нормы, синий — реже. Яркость = величина отклонения.", color = C.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WheelHeat(p, counts, order, Modifier.fillMaxWidth(0.72f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${a.spins.size}", color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("спинов", color = C.Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                FreqBars(counts, p)
                Text("Пунктир — ожидаемое число выпадений при равномерности.", color = C.Muted, fontSize = 11.sp)
            }
        }

        item { SectionLabel("Проверка гипотез") }
        if (a.tests.isEmpty()) item { Text("Нужно больше данных", color = C.Muted) }
        items(a.tests) { t ->
            GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.name, color = C.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (t.pValue != null) {
                        val sig = t.note.startsWith("Отклонение")
                        Pill(if (sig) "значимо" else "норма", if (sig) C.Bad else C.Good)
                    }
                }
                Text(t.description, color = C.Muted, fontSize = 12.sp)
                if (t.pValue != null) Text("статистика ${fmt(t.statistic, 2)} • p = ${fmt(t.pValue, 3)}", color = C.Text, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp))
                Text(t.note, color = C.Muted, fontSize = 12.sp)
            }
        }
        item {
            Text("Порог значимости с поправкой на 3 теста: p < 0,0033. Случайно «значимыми» бывают ~1 из 100 тестов — одиночные срабатывания ничего не доказывают.",
                color = C.Muted, fontSize = 11.sp, lineHeight = 15.sp)
        }

        item { SectionLabel("Частоты по секторам") }
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("По номеру", "По частоте", "По отклонению").forEachIndexed { i, s ->
                    FilterChip(sortMode == i, { sortMode = i }, { Text(s, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Violet.copy(alpha = 0.25f), selectedLabelColor = C.Text, labelColor = C.Muted))
                }
            }
            HeaderRow()
        }
        items(rows) { s ->
            val dev = abs(s.z) > 3
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(34.dp)) { SectorBall(a.names[s.sector], sectorColor(p, s.sector), 26.dp) }
                Cell(pct(s.total), 1.2f, C.Text, bold = true)
                Cell(s.w10?.let { pct(it, 0) } ?: "–")
                Cell(s.w25?.let { pct(it, 0) } ?: "–")
                Cell(s.w50?.let { pct(it, 0) } ?: "–")
                Cell(s.w100?.let { pct(it, 0) } ?: "–")
                Cell(s.trend?.let { signed(it, 0) } ?: "–", color = when { s.trend == null -> C.Muted; s.trend > 0 -> C.Violet; s.trend < 0 -> C.Blue; else -> C.Muted })
                Cell(signed(s.deviation, 1), 1f, if (dev) C.Warn else C.Muted)
            }
            HorizontalDivider(color = C.Line.copy(alpha = 0.5f))
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Δ25 — изменение: последние 25 против предыдущих 25 (п.п.). Откл. — разница с равномерной частотой 1/$k (п.п.); жёлтым — отклонение больше 3σ.",
                color = C.Muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Spacer(Modifier.width(34.dp))
        listOf("Всего" to 1.2f, "10" to 1f, "25" to 1f, "50" to 1f, "100" to 1f, "Δ25" to 1f, "Откл." to 1f).forEach { (t, w) ->
            Text(t, color = C.Muted, fontSize = 11.sp, modifier = Modifier.weight(w), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float = 1f, color: Color = C.Muted, bold: Boolean = false) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.weight(weight), textAlign = TextAlign.End, maxLines = 1)
}
