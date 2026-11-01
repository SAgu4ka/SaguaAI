package ru.sagu4ka.rngai.ml

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/** Прогноз следующего раунда. */
class Forecast(
    val round: Int,                    // номер следующего раунда (1-based)
    val expertProbs: List<DoubleArray>,
    val ensemble: DoubleArray,         // что думает ансамбль (теневой прогноз)
    val shown: DoubleArray,            // что показываем пользователю
    val edgeActive: Boolean,           // доказано ли преимущество над случайным
    val weights: DoubleArray,
    val logWealth: Double,             // ln накопленного «капитала» против равномерной модели
    val pBound: Double,                // верхняя граница p-value (неравенство Виля)
    val recentGain: Double,            // выигрыш в лог-правдоподобии за последние 200 спинов
    val observations: Int
) {
    val top: Int get() = shown.indices.maxByOrNull { shown[it] } ?: 0
    fun ranking(): List<Int> = shown.indices.sortedByDescending { shown[it] }
}

/** Запись журнала: что было показано, что выпало. */
class StepRecord(
    val index: Int,                    // номер раунда (1-based)
    val outcome: Int,
    val shownTop: IntArray,            // топ-3 показанного прогноза
    val shownTopP: DoubleArray,
    val pOutcome: Double,              // вероятность выпавшего сектора в показанном прогнозе
    val hitTop1: Boolean,
    val hitTop2: Boolean,
    val edgeActive: Boolean
)

/** Накопитель метрик одной модели. */
class MetricAcc(private val k: Int) {
    var n = 0; private set
    var top1 = 0; private set
    var top2 = 0; private set
    var brierSum = 0.0; private set
    var logLossSum = 0.0; private set

    fun add(p: DoubleArray, x: Int) {
        n++
        val order = p.indices.sortedByDescending { p[it] }
        if (order[0] == x) top1++
        if (order[0] == x || order[1] == x) top2++
        var b = 0.0
        for (j in 0 until k) { val d = p[j] - (if (j == x) 1.0 else 0.0); b += d * d }
        brierSum += b
        logLossSum += -ln(p[x].coerceAtLeast(1e-9))
    }

    fun snapshot(): MetricAcc = MetricAcc(k).also {
        it.n = n; it.top1 = top1; it.top2 = top2; it.brierSum = brierSum; it.logLossSum = logLossSum
    }

    val top1Rate get() = if (n == 0) 0.0 else top1.toDouble() / n
    val top2Rate get() = if (n == 0) 0.0 else top2.toDouble() / n
    val brier get() = if (n == 0) 0.0 else brierSum / n
    val logLoss get() = if (n == 0) 0.0 else logLossSum / n
}

class CalibBin(val label: String, val lo: Double, val hi: Double) {
    var count = 0L; var sumP = 0.0; var hits = 0L
    val meanP get() = if (count == 0L) 0.0 else sumP / count
    val observed get() = if (count == 0L) 0.0 else hits.toDouble() / count
    fun snapshot() = CalibBin(label, lo, hi).also { it.count = count; it.sumP = sumP; it.hits = hits }
}

class Report(
    val k: Int,
    val n: Int,
    val titles: List<String>,
    val groups: List<String>,
    val metrics: List<MetricAcc>,          // по экспертам
    val ensemble: MetricAcc,
    val shown: MetricAcc,
    val uniformBrier: Double,
    val uniformTop1: Double,
    val uniformTop2: Double,
    val uniformLogLoss: Double,
    val calibration: List<CalibBin>
)

/**
 * Онлайн-движок. Всё, что он знает, — история. Он детерминирован: если проиграть ту же историю,
 * получится то же состояние (так приложение восстанавливается после перезапуска и после
 * правки/удаления записей). Обучение строго «сначала прогноз — потом исход».
 */
class Engine(val k: Int, wheel: IntArray = Wheel.order(k), private val eta: Double = 0.35, private val decay: Double = 0.995) {

    private val laplace = LaplaceExpert(k)
    val experts: List<Expert>
    private val loss: DoubleArray

    private val m1: MarkovExpert
    val net: NeuralExpert

    init {
        m1 = MarkovExpert(k, 1, laplace)
        val m2 = MarkovExpert(k, 2, m1)
        net = NeuralExpert(k, wheel)
        experts = listOf(
            UniformExpert(k), laplace,
            WindowExpert(k, 10), WindowExpert(k, 25), WindowExpert(k, 50), WindowExpert(k, 100),
            m1, m2,
            SequenceExpert(k, laplace),
            WheelKernelExpert(k, wheel), DeltaExpert(k, wheel),
            net
        )
        loss = DoubleArray(experts.size)
    }

    var n = 0; private set
    var logWealth = 0.0; private set
    private val recent = ArrayDeque<Double>()          // лог-выигрыш ансамбля над равномерной за RECENT_WINDOW спинов
    private var recentSum = 0.0

    private val expertMetrics = List(experts.size) { MetricAcc(k) }
    private val ensMetrics = MetricAcc(k)
    private val shownMetrics = MetricAcc(k)
    private val calib = listOf(
        CalibBin("<0.7", 0.0, 0.7), CalibBin("0.7–0.85", 0.7, 0.85), CalibBin("0.85–1.0", 0.85, 1.0),
        CalibBin("1.0–1.15", 1.0, 1.15), CalibBin("1.15–1.3", 1.15, 1.3), CalibBin("1.3–1.6", 1.3, 1.6),
        CalibBin("1.6–2.2", 1.6, 2.2), CalibBin(">2.2", 2.2, Double.MAX_VALUE)
    )
    val records = ArrayList<StepRecord>()

    private var current: Forecast? = null

    /** Порог: ln(1/alpha) при alpha = 1%. Ville: P(капитал когда-либо ≥ 100 | случайность) ≤ 1%. */
    companion object {
        val THRESHOLD = ln(100.0)
        const val MIN_OBS = 200
        const val RECENT_WINDOW = 200     // окно «недавней формы» ансамбля
        const val RECENT_MIN = 0.75       // минимум нат выигрыша над случайным за окно
        fun readiness(n: Int) = (n / 500.0).coerceIn(0.0, 1.0)
    }

    private fun weights(): DoubleArray {
        val mn = loss.min()
        val w = DoubleArray(loss.size) { exp(-eta * (loss[it] - mn)) }
        val s = w.sum()
        return DoubleArray(w.size) { w[it] / s }
    }

    private fun edgeActive() = n >= MIN_OBS && logWealth >= THRESHOLD && recentSum > RECENT_MIN

    fun forecast(): Forecast {
        current?.let { return it }
        val probs = experts.map { e -> e.predict().normalized().map { it.coerceAtLeast(1e-9) }.toDoubleArray().normalized() }
        val w = weights()
        val ens = DoubleArray(k)
        for (i in probs.indices) for (j in 0 until k) ens[j] += w[i] * probs[i][j]
        val active = edgeActive()
        val f = Forecast(
            round = n + 1, expertProbs = probs, ensemble = ens,
            shown = if (active) ens else uniform(k),
            edgeActive = active, weights = w, logWealth = logWealth,
            pBound = min(1.0, exp(-logWealth)), recentGain = recentSum, observations = n
        )
        current = f
        return f
    }

    /** Новый результат. Возвращает запись журнала. Обучение происходит здесь. */
    fun step(x: Int): StepRecord {
        val f = forecast()

        // 1) оценка ДО обучения
        for (i in experts.indices) {
            expertMetrics[i].add(f.expertProbs[i], x)
            loss[i] = loss[i] * decay - ln(f.expertProbs[i][x])
        }
        ensMetrics.add(f.ensemble, x)
        shownMetrics.add(f.shown, x)
        recordCalibration(f.shown, x)

        // 2) e-процесс: капитал ансамбля против равномерной модели
        val gain = ln(f.ensemble[x] * k)
        logWealth += gain
        recent.addLast(gain); recentSum += gain
        if (recent.size > RECENT_WINDOW) recentSum -= recent.removeFirst()

        val order = f.ranking()
        val rec = StepRecord(
            index = n + 1, outcome = x,
            shownTop = intArrayOf(order[0], order[1], order[2]),
            shownTopP = doubleArrayOf(f.shown[order[0]], f.shown[order[1]], f.shown[order[2]]),
            pOutcome = f.shown[x],
            hitTop1 = f.top == x,
            hitTop2 = f.top == x || order[1] == x,
            edgeActive = f.edgeActive
        )
        records.add(rec)
        wealthHist.add(logWealth)

        // 3) обучение на новом результате
        for (e in experts) e.update(x)
        n++
        current = null
        return rec
    }

    private fun recordCalibration(p: DoubleArray, x: Int) {
        for (j in 0 until k) {
            val r = p[j] * k
            val bin = calib.first { r >= it.lo && r < it.hi }
            bin.count++; bin.sumP += p[j]; if (j == x) bin.hits++
        }
    }

    fun report(): Report = Report(
        k = k, n = n,
        titles = experts.map { it.title }, groups = experts.map { it.group },
        metrics = expertMetrics.map { it.snapshot() }, ensemble = ensMetrics.snapshot(), shown = shownMetrics.snapshot(),
        uniformBrier = (k - 1.0) / k, uniformTop1 = 1.0 / k, uniformTop2 = 2.0 / k, uniformLogLoss = ln(k.toDouble()),
        calibration = calib.map { it.snapshot() }
    )

    /** Накопленный ln-капитал после каждого спина (для графика). */
    val wealthHist = ArrayList<Double>()
}

/** Удобная обёртка: проиграть историю и вести серию капитала. */
fun Engine.replayAll(history: List<Int>) {
    for (x in history) step(x)
}
