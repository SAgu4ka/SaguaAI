package ru.sagu4ka.rngai.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.sagu4ka.rngai.Analysis
import ru.sagu4ka.rngai.data.ProfileEntity
import ru.sagu4ka.rngai.ml.Engine

@Composable
fun ProfileChip(profiles: List<ProfileEntity>, active: ProfileEntity, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(C.Bg2).border(1.dp, C.Line, RoundedCornerShape(50))
                .clickable { open = true }.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(active.name, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Icon(Icons.Rounded.ExpandMore, null, tint = C.Muted)
        }
        DropdownMenu(open, { open = false }, containerColor = C.Bg2) {
            profiles.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name, color = if (p.id == active.id) C.Violet else C.Text) },
                    onClick = { open = false; onSelect(p.id) }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(a: Analysis, profiles: List<ProfileEntity>, onSelect: (Long) -> Unit, goInput: () -> Unit) {
    val f = a.forecast
    val p = a.profile
    val k = p.sectorCount
    val n = f.observations
    var showAll by remember { mutableStateOf(false) }
    var showShadow by remember { mutableStateOf(false) }

    val evidence = (f.logWealth / Engine.THRESHOLD).toFloat().coerceIn(0f, 1f)
    val readiness = Engine.readiness(n).toFloat()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        ScreenTitle("Прогноз", "Раунд №${f.round} • ${p.name}") { ProfileChip(profiles, p, onSelect) }

        // ---------- главная карточка
        GlassCard(Modifier.fillMaxWidth(), glow = f.edgeActive) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RingGauge(evidence, if (f.edgeActive) "ЕСТЬ" else "${(evidence * 100).toInt()}%", "Доказательства\nпреимущества",
                    color = if (f.edgeActive) androidx.compose.ui.graphics.Brush.linearGradient(listOf(C.Good, C.Cyan)) else C.Accent)
                RingGauge(readiness, "$n", "Данные\n(цель 500)")
            }
            Spacer(Modifier.height(14.dp))

            when {
                n == 0 -> Banner("Добавьте первые результаты на вкладке «Ввод». До накопления данных все сектора считаются равновероятными.", C.Blue, "Пока пусто")
                n < Engine.MIN_OBS -> Banner(
                    "Собрано $n из минимум ${Engine.MIN_OBS} результатов. Пока данных недостаточно, чтобы отличить закономерность от случайности — все сектора показаны как равновероятные (${pct(1.0 / k, 2)}).",
                    C.Warn, "Данных недостаточно"
                )
                !f.edgeActive -> Banner(
                    "Статистически значимого преимущества над случайным угадыванием не обнаружено. Исторические данные не дают оснований повышать вероятность какого-либо сектора: у каждого ${pct(1.0 / k, 2)}.",
                    C.Warn, "Закономерностей не найдено"
                )
                else -> Banner(
                    "Ансамбль моделей стабильно обгоняет случайное угадывание на неизвестных ему данных (вероятность случайности ≤ ${pct(f.pBound, 2)}). Но преимущество может исчезнуть, если механика изменится.",
                    C.Good, "Обнаружено преимущество"
                )
            }
            Spacer(Modifier.height(14.dp))

            if (f.edgeActive) {
                val rank = f.ranking()
                val maxP = f.shown[rank[0]]
                Text("ПРОГНОЗ СЛЕДУЮЩЕГО РАУНДА", color = C.Muted, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                val shownCount = if (showAll) k else minOf(5, k)
                for (i in 0 until shownCount) {
                    val s = rank[i]
                    ProbRow(i + 1, a.names[s], sectorColor(p, s), f.shown[s], maxP, highlight = i == 0)
                }
                if (k > 5) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Свернуть" else "Показать все сектора ($k)", color = C.Violet)
                        Icon(if (showAll) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = C.Violet)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val top = rank[0]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Наиболее вероятный:  ", color = C.Muted, fontSize = 13.sp)
                    SectorBall(a.names[top], sectorColor(p, top), 36.dp, Modifier.alpha(pulse()))
                    Text("  ${a.names[top]} — ${pct(f.shown[top], 2)}", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else if (n > 0) {
                // честный режим: равномерный прогноз
                Text("ПРОГНОЗ СЛЕДУЮЩЕГО РАУНДА", color = C.Muted, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Любой сектор — ${pct(1.0 / k, 2)}", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Выделять сектор было бы самообманом.", color = C.Muted, fontSize = 12.sp)

                TextButton(onClick = { showShadow = !showShadow }) {
                    Text(if (showShadow) "Скрыть теневой прогноз" else "Показать теневой прогноз ансамбля (не доказан)", color = C.Muted, fontSize = 12.sp)
                }
                if (showShadow) {
                    val sr = f.ensemble.indices.sortedByDescending { f.ensemble[it] }
                    for (i in 0 until minOf(5, k)) {
                        val s = sr[i]
                        ProbRow(i + 1, a.names[s], sectorColor(p, s), f.ensemble[s], f.ensemble[sr[0]], muted = true)
                    }
                    Text("Это то, что сказала бы модель, если бы ей доверять. Статистически не подтверждено — это шум.", color = C.Muted, fontSize = 11.sp)
                }
            }
        }

        // ---------- сводка
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("наблюдений", "$n")
                Stat("p-value ≤", if (n == 0) "—" else fmt(f.pBound, 3), if (f.pBound < 0.01) C.Good else C.Text)
                val lead = f.weights.indices.maxByOrNull { f.weights[it] } ?: 0
                Stat("ведущая модель", a.report.groups[lead].let { g -> groupName(g) } + " ${(f.weights[lead] * 100).toInt()}%")
            }
            Spacer(Modifier.height(10.dp))
            Text("Дообучений нейросети: ${a.netSteps}", color = C.Muted, fontSize = 12.sp)
        }

        // ---------- последние результаты
        if (a.spins.isNotEmpty()) {
            SectionLabel("Последние результаты")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                a.spins.takeLast(14).reversed().forEach { SectorBall(a.names[it.sector], sectorColor(p, it.sector), 38.dp) }
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Добавить результат", Modifier.fillMaxWidth(), onClick = goInput)
        Spacer(Modifier.height(10.dp))
        Text(
            "Исследовательский инструмент. Если генератор ROB RUN честный, прогнозировать нечего, и приложение так и скажет. Не ставьте деньги на «прогноз».",
            color = C.Muted, fontSize = 11.sp, lineHeight = 15.sp
        )
        Spacer(Modifier.height(8.dp))
    }
}

fun groupName(g: String) = when (g) {
    "A" -> "Базовая"; "B" -> "Окна"; "C" -> "Марков"; "D" -> "Повторы"
    "W" -> "Колесо"; "N" -> "Нейросеть"; else -> "Контроль"
}
