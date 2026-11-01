package ru.sagu4ka.rngai.ml

import kotlin.math.exp

/** Геометрия колеса. Для 37 секторов — европейская рулетка, иначе просто кольцо по номерам. */
object Wheel {
    val EURO = intArrayOf(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14,
        31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    )
    val RED = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)

    fun order(k: Int): IntArray = if (k == 37) EURO.copyOf() else IntArray(k) { it }

    fun positions(order: IntArray): IntArray {
        val pos = IntArray(order.size)
        order.forEachIndexed { i, s -> pos[s] = i }
        return pos
    }
}

/**
 * Эксперт = одна модель прогноза. Контракт строго хронологический:
 * predict() вызывается ДО того, как исход известен, update(x) — после.
 */
interface Expert {
    val id: String
    val title: String
    val group: String       // A, B, C, D, W (колесо), N (нейросеть), U (контроль)
    fun predict(): DoubleArray
    fun update(x: Int)
}

internal fun DoubleArray.normalized(): DoubleArray {
    val s = sum()
    return if (s <= 0.0) DoubleArray(size) { 1.0 / size } else DoubleArray(size) { this[it] / s }
}

internal fun uniform(k: Int) = DoubleArray(k) { 1.0 / k }

/** Контроль: равномерное распределение. Ансамбль всегда может на него откатиться. */
class UniformExpert(private val k: Int) : Expert {
    override val id = "uniform"
    override val title = "Равномерная (контроль)"
    override val group = "U"
    override fun predict() = uniform(k)
    override fun update(x: Int) {}
}

/** Модель A: историческая частота со сглаживанием Лапласа (alpha = 1). */
class LaplaceExpert(private val k: Int) : Expert {
    override val id = "laplace"
    override val title = "Историческая частота + Лаплас"
    override val group = "A"
    private val c = IntArray(k)
    private var n = 0
    override fun predict(): DoubleArray = DoubleArray(k) { (c[it] + 1.0) / (n + k) }
    override fun update(x: Int) { c[x]++; n++ }
}

/** Модель B: скользящее окно последних [window] результатов, усадка к равномерному. */
class WindowExpert(private val k: Int, private val window: Int, private val strength: Double = k.toDouble()) : Expert {
    override val id = "win$window"
    override val title = "Скользящее окно $window"
    override val group = "B"
    private val c = IntArray(k)
    private val ring = IntArray(window)
    private var n = 0
    override fun predict(): DoubleArray {
        val m = minOf(n, window)
        return DoubleArray(k) { (c[it] + strength / k) / (m + strength) }
    }
    override fun update(x: Int) {
        if (n >= window) c[ring[n % window]]--
        ring[n % window] = x
        c[x]++
        n++
    }
}

/** Модель C: марковская цепь порядка [order] с иерархической усадкой к модели нижнего порядка. */
class MarkovExpert(
    private val k: Int,
    private val order: Int,
    private val prior: Expert,
    private val strength: Double = 25.0
) : Expert {
    override val id = "markov$order"
    override val title = "Марковская цепь, порядок $order"
    override val group = "C"
    private val table = HashMap<Long, IntArray>()
    private val hist = ArrayList<Int>()

    private fun key(): Long? {
        if (hist.size < order) return null
        var h = 0L
        for (i in hist.size - order until hist.size) h = h * k + hist[i]
        return h
    }

    override fun predict(): DoubleArray {
        val p0 = prior.predict()
        val cnt = key()?.let { table[it] } ?: return p0
        val tot = cnt.sum()
        return DoubleArray(k) { (cnt[it] + strength * p0[it]) / (tot + strength) }
    }

    override fun update(x: Int) {
        key()?.let { table.getOrPut(it) { IntArray(k) }[x]++ }
        hist.add(x)
    }
}

/**
 * Модель D: поиск повторяющихся последовательностей. Находит самое длинное совпадение
 * текущего «хвоста» с прошлым (длины 2..8) и ставит часть вероятности на то, что
 * шло за ним тогда. Вес зависит от длины совпадения; значимость решает ансамбль.
 */
class SequenceExpert(private val k: Int, private val prior: Expert, private val maxLen: Int = 8) : Expert {
    override val id = "seq"
    override val title = "Повторяющиеся последовательности"
    override val group = "D"
    private val hist = ArrayList<Int>()
    private val last = Array(maxLen + 1) { HashMap<Long, Int>() }

    private fun key(len: Int): Long {
        var h = 1469598103934665603L
        for (i in hist.size - len until hist.size) h = (h xor (hist[i] + 1).toLong()) * 1099511628211L
        return h
    }

    override fun predict(): DoubleArray {
        val p0 = prior.predict()
        for (len in minOf(maxLen, hist.size) downTo 2) {
            val idx = last[len][key(len)] ?: continue
            val next = hist[idx]
            val w = minOf(0.30, 0.03 * len)
            return DoubleArray(k) { p0[it] * (1 - w) + (if (it == next) w else 0.0) }
        }
        return p0
    }

    override fun update(x: Int) {
        for (len in 2..minOf(maxLen, hist.size)) last[len][key(len)] = hist.size
        hist.add(x)
    }
}

/**
 * Физическое смещение колеса: счётчики недавних выпадений размываются на соседние
 * карманы (радиус 2). Если колесо «любит» участок — это увидит именно она.
 */
class WheelKernelExpert(private val k: Int, wheel: IntArray, private val window: Int = 200) : Expert {
    override val id = "wheel"
    override val title = "Смещение колеса (соседние карманы)"
    override val group = "W"
    private val pos = Wheel.positions(wheel)
    private val wheelOrder = wheel
    private val ring = IntArray(window)
    private var n = 0
    private val mass = DoubleArray(k)       // по позициям на колесе
    private val kernel = doubleArrayOf(1.0, 0.6, 0.25)
    private val kSum = kernel[0] + 2 * (kernel[1] + kernel[2])

    private fun add(s: Int, sign: Double) {
        val p = pos[s]
        for (d in -2..2) {
            val q = ((p + d) % k + k) % k
            mass[q] += sign * kernel[kotlin.math.abs(d)] / kSum
        }
    }

    override fun predict(): DoubleArray {
        val m = minOf(n, window)
        val strength = k.toDouble()
        val out = DoubleArray(k)
        for (s in 0 until k) out[s] = (mass[pos[s]].coerceAtLeast(0.0) + strength / k) / (m + strength)
        return out.normalized()
    }

    override fun update(x: Int) {
        if (n >= window) add(ring[n % window], -1.0)
        ring[n % window] = x
        add(x, 1.0)
        n++
    }
}

/**
 * «Подпись крупье»: распределение шага между двумя соседними выпадениями
 * (сколько карманов прошло). Старые данные затухают — ловит смену механики.
 */
class DeltaExpert(private val k: Int, wheel: IntArray, private val forget: Double = 0.997) : Expert {
    override val id = "delta"
    override val title = "Шаг по колесу (подпись броска)"
    override val group = "W"
    private val pos = Wheel.positions(wheel)
    private val order = wheel
    private val c = DoubleArray(k)
    private var total = 0.0
    private var prev = -1

    override fun predict(): DoubleArray {
        if (prev < 0) return uniform(k)
        val strength = k.toDouble()
        val out = DoubleArray(k)
        for (d in 0 until k) {
            val sector = order[(pos[prev] + d) % k]
            out[sector] = (c[d] + strength / k) / (total + strength)
        }
        return out.normalized()
    }

    override fun update(x: Int) {
        if (prev >= 0) {
            for (i in c.indices) c[i] *= forget
            total *= forget
            val d = ((pos[x] - pos[prev]) % k + k) % k
            c[d] += 1.0
            total += 1.0
        }
        prev = x
    }
}

internal fun softmax(z: DoubleArray): DoubleArray {
    val m = z.max()
    val e = DoubleArray(z.size) { exp(z[it] - m) }
    val s = e.sum()
    return DoubleArray(z.size) { e[it] / s }
}
