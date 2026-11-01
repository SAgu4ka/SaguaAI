package ru.sagu4ka.rngai.ml

import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Онлайн-нейросеть: перцептрон с одним скрытым слоем (tanh) и softmax на выходе.
 * Обучается прямо во время использования: после каждого нового результата делает шаг
 * градиентного спуска + 2 шага по случайным примерам из недавнего буфера.
 *
 * Выходной слой стартует с нулей, поэтому до обучения сеть выдаёт ровно 1/K.
 * Вход (6·K): последние 3 выпадения (one-hot), частоты за 25 и 100 спинов,
 * шаг по колесу между двумя последними выпадениями.
 */
class NeuralExpert(
    private val k: Int,
    wheel: IntArray,
    private val hidden: Int = 24,
    seed: Long = 7L,
    private val lr: Double = 0.02,
    private val l2: Double = 1e-3,
    private val replay: Int = 2
) : Expert {
    override val id = "net"
    override val title = "Нейросеть (онлайн-обучение)"
    override val group = "N"

    private val pos = Wheel.positions(wheel)
    private val inDim = 6 * k
    private val rnd = Random(seed)

    private val w1 = Array(hidden) { DoubleArray(inDim) { (rnd.nextDouble() - 0.5) * 2 * sqrt(3.0 / inDim) } }
    private val b1 = DoubleArray(hidden)
    private val w2 = Array(k) { DoubleArray(hidden) }
    private val b2 = DoubleArray(k)

    private val hist = ArrayList<Int>()
    private val c25 = IntArray(k)
    private val c100 = IntArray(k)

    private class Sample(val x: DoubleArray, val y: Int)
    private val buffer = ArrayList<Sample>()
    private var cachedX: DoubleArray? = null

    var trainSteps = 0L
        private set

    private fun features(): DoubleArray {
        val x = DoubleArray(inDim)
        val n = hist.size
        if (n >= 1) x[hist[n - 1]] = 1.0
        if (n >= 2) x[k + hist[n - 2]] = 1.0
        if (n >= 3) x[2 * k + hist[n - 3]] = 1.0
        val n25 = minOf(n, 25)
        val n100 = minOf(n, 100)
        for (j in 0 until k) {
            if (n25 > 0) x[3 * k + j] = 0.5 * (c25[j].toDouble() / n25 * k - 1.0)
            if (n100 > 0) x[4 * k + j] = 0.5 * (c100[j].toDouble() / n100 * k - 1.0)
        }
        if (n >= 2) {
            val d = ((pos[hist[n - 1]] - pos[hist[n - 2]]) % k + k) % k
            x[5 * k + d] = 1.0
        }
        return x
    }

    private fun hiddenOf(x: DoubleArray): DoubleArray {
        val h = DoubleArray(hidden)
        for (j in 0 until hidden) {
            var s = b1[j]
            val w = w1[j]
            for (i in 0 until inDim) if (x[i] != 0.0) s += w[i] * x[i]
            h[j] = tanh(s)
        }
        return h
    }

    private fun probsOf(h: DoubleArray): DoubleArray {
        val z = DoubleArray(k)
        for (i in 0 until k) {
            var s = b2[i]
            val w = w2[i]
            for (j in 0 until hidden) s += w[j] * h[j]
            z[i] = s
        }
        return softmax(z)
    }

    override fun predict(): DoubleArray {
        val x = features()
        cachedX = x
        return probsOf(hiddenOf(x))
    }

    private fun trainOn(x: DoubleArray, y: Int, rate: Double) {
        val h = hiddenOf(x)
        val p = probsOf(h)
        val dz = DoubleArray(k) { p[it] - (if (it == y) 1.0 else 0.0) }
        val dh = DoubleArray(hidden)
        for (i in 0 until k) for (j in 0 until hidden) dh[j] += dz[i] * w2[i][j]
        for (i in 0 until k) {
            val w = w2[i]
            for (j in 0 until hidden) w[j] -= rate * (dz[i] * h[j] + l2 * w[j])
            b2[i] -= rate * dz[i]
        }
        for (j in 0 until hidden) {
            val g = dh[j] * (1.0 - h[j] * h[j])
            if (g == 0.0) continue
            val w = w1[j]
            for (i in 0 until inDim) {
                val xi = x[i]
                if (xi != 0.0) w[i] -= rate * (g * xi + l2 * w[i])
            }
            b1[j] -= rate * g
        }
        trainSteps++
    }

    override fun update(x: Int) {
        val feat = cachedX ?: features()
        trainOn(feat, x, lr)
        if (buffer.isNotEmpty()) repeat(replay) {
            val s = buffer[rnd.nextInt(buffer.size)]
            trainOn(s.x, s.y, lr * 0.5)
        }
        buffer.add(Sample(feat, x))
        if (buffer.size > 256) buffer.removeAt(0)
        cachedX = null

        // обновить историю и окна
        hist.add(x)
        c25[x]++; c100[x]++
        val n = hist.size
        if (n > 25) c25[hist[n - 26]]--
        if (n > 100) c100[hist[n - 101]]--
    }
}
