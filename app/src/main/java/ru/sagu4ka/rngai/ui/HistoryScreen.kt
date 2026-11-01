package ru.sagu4ka.rngai.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.sagu4ka.rngai.Analysis
import ru.sagu4ka.rngai.data.SpinEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(a: Analysis, onSetSector: (Long, Int) -> Unit, onDelete: (Long) -> Unit) {
    val p = a.profile
    var editing by remember { mutableStateOf<Pair<Int, SpinEntity>?>(null) }
    val timeFmt = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale.forLanguageTag("ru-RU")) }

    val shownRecs = a.records.filter { it.edgeActive }
    val hit1 = shownRecs.count { it.hitTop1 }
    val hit2 = shownRecs.count { it.hitTop2 }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        item { ScreenTitle("История", "Нажмите на запись, чтобы исправить или удалить") }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Журнал прогнозов", color = C.Text, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                if (shownRecs.isEmpty()) {
                    Text("Прогнозы с доказанным преимуществом ещё не выдавались. Во всех раундах приложение честно показывало равномерную вероятность.", color = C.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("прогнозов", "${shownRecs.size}")
                        Stat("попал в топ-1", "$hit1 (${pct(hit1.toDouble() / shownRecs.size)})")
                        Stat("попал в топ-2", "$hit2 (${pct(hit2.toDouble() / shownRecs.size)})")
                    }
                    Text("Случайное угадывание: топ-1 ${pct(1.0 / p.sectorCount)}, топ-2 ${pct(2.0 / p.sectorCount)}.", color = C.Muted, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (a.spins.isEmpty()) item { Text("Пока нет записей.", color = C.Muted) }

        val reversed = a.spins.indices.reversed().toList()
        itemsIndexed(reversed) { _, idx ->
            val s = a.spins[idx]
            val rec = a.records.getOrNull(idx)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(C.Bg1)
                    .clickable { editing = idx to s }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("№${idx + 1}", color = C.Muted, fontSize = 12.sp, modifier = Modifier.width(48.dp))
                SectorBall(a.names[s.sector], sectorColor(p, s.sector), 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.names[s.sector], color = C.Text, fontWeight = FontWeight.SemiBold)
                    Text(timeFmt.format(Date(s.ts)), color = C.Muted, fontSize = 11.sp)
                }
                if (rec != null) {
                    if (rec.edgeActive) {
                        val col = if (rec.hitTop1) C.Good else if (rec.hitTop2) C.Warn else C.Muted
                        Column(horizontalAlignment = Alignment.End) {
                            Text("прогноз: ${a.names[rec.shownTop[0]]} ${pct(rec.shownTopP[0])}", color = C.Muted, fontSize = 11.sp)
                            Text(if (rec.hitTop1) "попал" else if (rec.hitTop2) "в топ-2" else "мимо", color = col, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else Text("без прогноза", color = C.Line, fontSize = 11.sp)
                }
            }
        }
    }

    editing?.let { (idx, s) ->
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = C.Bg2,
            title = { Text("Запись №${idx + 1}", color = C.Text) },
            text = {
                Column {
                    Text("Выберите правильный сектор:", color = C.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(GridCells.Fixed(5), Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((0 until p.sectorCount).toList()) { i ->
                            Box(
                                Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                                    .background(sectorColor(p, i).copy(alpha = if (i == s.sector) 1f else 0.55f))
                                    .border(if (i == s.sector) 2.dp else 0.dp, androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp))
                                    .clickable { onSetSector(s.id, i); editing = null },
                                contentAlignment = Alignment.Center
                            ) { Text(a.names[i], color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = if (a.names[i].length > 3) 10.sp else 15.sp, maxLines = 2) }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ onDelete(s.id); editing = null }) { Text("Удалить запись", color = C.Bad) } },
            dismissButton = { TextButton({ editing = null }) { Text("Закрыть", color = C.Muted) } }
        )
    }
}
