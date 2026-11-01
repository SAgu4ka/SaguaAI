import ru.sagu4ka.rngai.ml.*
import kotlin.random.Random

const val K = 37
val W = Wheel.order(K)
val POS = Wheel.positions(W)

fun fair(r: Random, n: Int) = List(n) { r.nextInt(K) }

/** Колесо смещено: сектор 17 и его соседи по колесу выпадают втрое чаще. */
fun biasedWheel(r: Random, n: Int): List<Int> {
    val hot = (-1..1).map { W[(POS[17] + it + K) % K] }.toSet()
    val weights = DoubleArray(K) { if (it in hot) 3.0 else 1.0 }
    val tot = weights.sum()
    return List(n) {
        var u = r.nextDouble() * tot; var s = 0
        while (u > weights[s]) { u -= weights[s]; s++ }
        s
    }
}

/** Зависимость: после x с вероятностью 20% идёт сектор (x+1)%K, иначе случайный. */
fun markov(r: Random, n: Int): List<Int> {
    val out = ArrayList<Int>(); var prev = 0
    repeat(n) { prev = if (r.nextDouble() < 0.2) (prev + 1) % K else r.nextInt(K); out.add(prev) }
    return out
}

/** Подпись броска: шаг по колесу часто равен +9..+11 карманов. */
fun deltaBias(r: Random, n: Int): List<Int> {
    val out = ArrayList<Int>(); var prev = 0
    repeat(n) {
        prev = if (r.nextDouble() < 0.25) W[(POS[prev] + 9 + r.nextInt(3)) % K] else r.nextInt(K)
        out.add(prev)
    }
    return out
}

/** Смена механики: первые 1500 — смещение, потом честно. */
fun regime(r: Random, n: Int) = biasedWheel(r, 1500) + fair(r, n - 1500)

class Out(val everOpen: Boolean, val firstOpen: Int, val everWealth: Boolean, val maxLogW: Double,
          val finalOpen: Boolean, val top1: Double, val brierGain: Double)

fun run(seq: List<Int>): Out {
    val e = Engine(K)
    var first = -1; var everW = false; var maxW = -1e9
    for ((i, x) in seq.withIndex()) {
        val f = e.forecast()
        if (f.edgeActive && first < 0) first = i
        if (f.logWealth >= Engine.THRESHOLD) everW = true
        if (f.logWealth > maxW) maxW = f.logWealth
        e.step(x)
    }
    val rep = e.report()
    return Out(first >= 0, first, everW, maxW, e.forecast().edgeActive, rep.shown.top1Rate, rep.uniformBrier - rep.shown.brier)
}

fun scenario(name: String, runs: Int, n: Int, gen: (Random, Int) -> List<Int>) {
    val t0 = System.currentTimeMillis()
    val res = (1..runs).map { run(gen(Random(1000L + it), n)) }
    val open = res.count { it.everOpen }
    val med = res.filter { it.everOpen }.map { it.firstOpen }.sorted().let { if (it.isEmpty()) -1 else it[it.size / 2] }
    println("%-28s runs=%-3d ever-open=%3d (%.0f%%)  median-first=%-5d  final-open=%3d  maxlogW(avg)=%.2f  top1=%.4f  brierGain=%.6f  [%d ms]"
        .format(name, runs, open, 100.0 * open / runs, med, res.count { it.finalOpen }, res.map { it.maxLogW }.average(),
            res.map { it.top1 }.average(), res.map { it.brierGain }.average(), System.currentTimeMillis() - t0))
}

fun main(args: Array<String>) {
    val runs = args.getOrNull(0)?.toInt() ?: 40
    val n = args.getOrNull(1)?.toInt() ?: 3000
    println("threshold ln(100) = %.3f, K=%d, spins=%d".format(Engine.THRESHOLD, K, n))
    scenario("1. Честное колесо (H0)", runs, n, ::fair)
    scenario("2. Смещённое колесо", minOf(runs, 20), n, ::biasedWheel)
    scenario("3. Зависимость x -> x+1", minOf(runs, 20), n, ::markov)
    scenario("4. Подпись броска (шаг)", minOf(runs, 20), n, ::deltaBias)
    scenario("5. Смена механики 1500", minOf(runs, 20), n, ::regime)

    // sanity-check хи-квадрата
    val c = IntArray(K).also { a -> fair(Random(5), 3700).forEach { a[it]++ } }
    val (chi, p) = StatsCalc.chiSquareUniform(c)
    println("chi2 sanity: chi=%.2f p=%.3f (ожидается p ~ равномерно на 0..1)".format(chi, p))
    println("chi2 survival(36.0, df=36)=%.4f (эталон ~0.4720)".format(StatsCalc.chiSqSurvival(36.0, 36)))
    println("chi2 survival(58.6, df=36)=%.4f (эталон ~0.0100)".format(StatsCalc.chiSqSurvival(58.619, 36)))
}
