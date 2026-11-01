package ru.sagu4ka.rngai.ml

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Частоты сектора в разных окнах. */
class SectorStat(
    val sector: Int,
    val total: Double,          // за всю историю, доля
    val w10: Double?, val w25: Double?, val w50: Double?, val w100: Double?,  // null если данных меньше окна
    val trend: Double?,         // последние 25 минус предыдущие 25, в п.п.
    val deviation: Double,      // отклонение от равномерного, в п.п.
    val z: Double               // z-оценка отклонения счёта от ожидаемого
)

class TestResult(val name: String, val description: String, val statistic: Double, val pValue: Double?, val note: String)

object StatsCalc {

    fun sectorStats(history: List<Int>, k: Int): List<SectorStat> {
        val n = history.size
        fun freq(window: Int, offset: Int = 0): DoubleArray? {
            if (n < window + offset) return null
            val from = n - window - offset
            val c = IntArray(k)
            for (i in from until from + window) c[history[i]]++
            return DoubleArray(k) { c[it].toDouble() / window }
        }
        val all = DoubleArray(k).also { for (x in history) it[x] += 1.0 / maxOf(1, n) }
        val f10 = freq(10); val f25 = freq(25); val f50 = freq(50); val f100 = freq(100)
        val prev25 = freq(25, 25)
        val p = 1.0 / k
        val sd = sqrt(n * p * (1 - p))
        return (0 until k).map { s ->
            SectorStat(
                sector = s, total = all[s],
                w10 = f10?.get(s), w25 = f25?.get(s), w50 = f50?.get(s), w100 = f100?.get(s),
                trend = if (f25 != null && prev25 != null) (f25[s] - prev25[s]) * 100 else null,
                deviation = (all[s] - p) * 100,
                z = if (n > 0 && sd > 0) (all[s] * n - n * p) / sd else 0.0
            )
        }
    }

    /** Критерий хи-квадрат равномерности: возвращает (статистика, p-value). */
    fun chiSquareUniform(counts: IntArray): Pair<Double, Double> {
        val n = counts.sum().toDouble()
        val k = counts.size
        val e = n / k
        var chi = 0.0
        for (c in counts) chi += (c - e) * (c - e) / e
        return chi to chiSqSurvival(chi, k - 1)
    }

    fun tests(history: List<Int>, k: Int, wheel: IntArray = Wheel.order(k)): List<TestResult> {
        val n = history.size
        val out = ArrayList<TestResult>()
        if (n < 5 * k) {
            out += TestResult("Данных мало", "Для надёжных тестов нужно хотя бы ${5 * k} результатов (сейчас $n).", 0.0, null, "Показатели ниже ориентировочные")
        }
        if (n >= 2) {
            val counts = IntArray(k).also { for (x in history) it[x]++ }
            val (chi, p) = chiSquareUniform(counts)
            out += TestResult("Равномерность секторов", "χ²: все сектора выпадают одинаково часто?", chi, p, verdict(p))

            val pos = Wheel.positions(wheel)
            val dc = IntArray(k)
            for (i in 1 until n) dc[((pos[history[i]] - pos[history[i - 1]]) % k + k) % k]++
            val (chi2, p2) = chiSquareUniform(dc)
            out += TestResult("Шаг по колесу", "χ²: не повторяется ли расстояние между соседними выпадениями?", chi2, p2, verdict(p2))

            var rep = 0
            for (i in 1 until n) if (history[i] == history[i - 1]) rep++
            val pr = 1.0 / k
            val mean = (n - 1) * pr
            val sd = sqrt((n - 1) * pr * (1 - pr))
            val z = if (sd > 0) (rep - mean) / sd else 0.0
            out += TestResult("Повторы подряд", "z-тест: число одинаковых выпадений подряд (ожидается ${"%.1f".format(mean)}, факт $rep).", z, 2 * normSurvival(abs(z)), verdict(2 * normSurvival(abs(z))))
        }
        return out
    }

    private fun verdict(p: Double): String {
        // поправка Бонферрони на 3 теста при уровне 1 %
        val alpha = 0.01 / 3
        return if (p < alpha) "Отклонение значимо" else "Согласуется со случайностью"
    }

    // ---- специальные функции ----
    fun normSurvival(z: Double): Double = 0.5 * erfc(z / sqrt(2.0))

    private fun erfc(x: Double): Double {
        val t = 1.0 / (1.0 + 0.5 * abs(x))
        val r = t * exp(-x * x - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 +
                t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 +
                t * (-0.82215223 + t * 0.17087277)))))))))
        return if (x >= 0) r else 2 - r
    }

    /** Хвост хи-квадрат = Q(df/2, x/2), регуляризованная верхняя неполная гамма-функция. */
    fun chiSqSurvival(x: Double, df: Int): Double {
        if (x <= 0) return 1.0
        return gammaQ(df / 2.0, x / 2.0)
    }

    private fun lnGamma(x: Double): Double {
        val c = doubleArrayOf(76.18009172947146, -86.50532032941677, 24.01409824083091,
            -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5)
        var y = x
        var tmp = x + 5.5
        tmp -= (x + 0.5) * ln(tmp)
        var ser = 1.000000000190015
        for (cj in c) { y += 1; ser += cj / y }
        return -tmp + ln(2.5066282746310005 * ser / x)
    }

    private fun gammaQ(a: Double, x: Double): Double {
        if (x < a + 1) {
            var ap = a; var sum = 1.0 / a; var del = sum
            for (i in 0 until 500) { ap += 1; del *= x / ap; sum += del; if (abs(del) < abs(sum) * 1e-12) break }
            return 1.0 - sum * exp(-x + a * ln(x) - lnGamma(a))
        }
        var b = x + 1 - a; var c = 1e300; var d = 1 / b; var h = d
        for (i in 1..500) {
            val an = -i * (i - a)
            b += 2
            d = an * d + b; if (abs(d) < 1e-300) d = 1e-300
            c = b + an / c; if (abs(c) < 1e-300) c = 1e-300
            d = 1 / d
            val del = d * c
            h *= del
            if (abs(del - 1) < 1e-12) break
        }
        return exp(-x + a * ln(x) - lnGamma(a)) * h
    }
}
