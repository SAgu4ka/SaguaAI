package ru.sagu4ka.rngai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.sagu4ka.rngai.Analysis
import ru.sagu4ka.rngai.ml.Engine

@Composable
fun ModelsScreen(a: Analysis) {
    val r = a.report
    val f = a.forecast
    val k = r.k
    val n = r.n

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        ScreenTitle("Модели", "Проверка на неизвестных данных: сначала прогноз, потом результат")

        // ---- итог проверки
        GlassCard(Modifier.fillMaxWidth(), glow = f.edgeActive) {
            Text("Показанный прогноз против случайного", color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            if (n == 0) Text("Данных пока нет", color = C.Muted) else {
                MetricLine("Топ-1", pct(r.shown.top1Rate, 2), "случайно " + pct(r.uniformTop1, 2))
                MetricLine("Топ-2", pct(r.shown.top2Rate, 2), "случайно " + pct(r.uniformTop2, 2))
                MetricLine("Brier Score", fmt(r.shown.brier, 5), "случайно " + fmt(r.uniformBrier, 5))
                MetricLine("Log-loss", fmt(r.shown.logLoss, 4), "случайно " + fmt(r.uniformLogLoss, 4))
                Text("Brier и log-loss: чем меньше, тем лучше. Если цифры совпадают со «случайно» — приложение честно показывало равномерную вероятность.",
                    color = C.Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        SectionLabel("Накопленное преимущество ансамбля")
        GlassCard(Modifier.fillMaxWidth()) {
            WealthChart(a.wealth, Engine.THRESHOLD)
            Spacer(Modifier.height(6.dp))
            Text("Линия — ln(капитал) ансамбля против равномерной модели. Жёлтый пунктир — порог: выше него шанс, что это случайность, меньше 1 %. Для честного ГСЧ линия блуждает около нуля.",
                color = C.Muted, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("сейчас", fmt(f.logWealth, 2), if (f.logWealth >= Engine.THRESHOLD) C.Good else C.Text)
                Stat("порог", fmt(Engine.THRESHOLD, 2))
                Stat("форма за ${Engine.RECENT_WINDOW}", signed(f.recentGain, 2))
            }
        }

        SectionLabel("Все модели")
        val leadOrder = r.titles.indices.sortedByDescending { f.weights[it] }
        for (i in leadOrder) {
            val m = r.metrics[i]
            GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.titles[i], color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Pill(groupName(r.groups[i]))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(f.weights[i].toFloat(), Modifier.weight(1f))
                    Text("  вес ${pct(f.weights[i], 1)}", color = C.Muted, fontSize = 12.sp)
                }
                if (n > 0) {
                    Spacer(Modifier.height(8.dp))
                    val dLL = r.uniformLogLoss - m.logLoss
                    val dB = r.uniformBrier - m.brier
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("топ-1", pct(m.top1Rate))
                        Stat("топ-2", pct(m.top2Rate))
                        Stat("Brier vs случ.", signed(dB * 10000, 1) + "e-4", if (dB > 0.00005) C.Good else if (dB < -0.00005) C.Bad else C.Muted)
                        Stat("log-loss vs случ.", signed(dLL * 1000, 1) + "‰", if (dLL > 0.0005) C.Good else if (dLL < -0.0005) C.Bad else C.Muted)
                    }
                }
            }
        }
        Text("Плюс — модель лучше случайной, минус — хуже. На честной рулетке почти все значения отрицательные: каждая модель «видит» закономерности в шуме и платит за это ошибкой.",
            color = C.Muted, fontSize = 11.sp, lineHeight = 15.sp)

        SectionLabel("Калибровка ансамбля")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Сходятся ли предсказанные и фактические частоты? По оси — доля от 1/$k.", color = C.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            CalibrationChart(r.calibration, k)
        }

        SectionLabel("Как это устроено")
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "• Нейросеть (скрытый слой, softmax) дообучается после каждого результата и до обучения выдаёт ровно 1/$k. Шагов обучения: ${a.netSteps}.\n" +
                "• Ансамбль взвешивает все модели по их недавним ошибкам на невиданных данных.\n" +
                "• Показ прогноза включается только когда накопленное преимущество проходит строгий тест (≈ 1 % ложных срабатываний даже при проверке после каждого спина) и держится в последних ${Engine.RECENT_WINDOW} спинах.\n" +
                "• Если генератор честный, преимущества не появится — и приложение так и покажет.",
                color = C.Text, fontSize = 12.sp, lineHeight = 17.sp
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MetricLine(name: String, value: String, ref: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = C.Muted, modifier = Modifier.width(92.dp), fontSize = 13.sp)
        Text(value, color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(ref, color = C.Muted, fontSize = 12.sp)
    }
}
