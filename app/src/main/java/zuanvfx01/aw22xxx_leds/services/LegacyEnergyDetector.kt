package zuanvfx01.aw22xxx_leds.services

import kotlin.math.sqrt

/**
 * Energy (dB) onset detector, used only as a fallback for material where the spectral
 * tracker finds no onsets. Its behaviour follows the selected [BeatMode]:
 *
 *  - AGGRESSIVE: the original detector. Higher sensitivity = lower threshold (easier).
 *  - PRECISION:  V6 detector. 1 = easier, 10 = stronger evidence only; longer spacing.
 *  - HYBRID:     V6 semantics with slightly easier thresholds and shorter spacing.
 *
 * It uses running sums (no per-hop allocation). One instance belongs to one capture run,
 * so it is never shared between threads.
 */
class LegacyEnergyDetector(private val mode: BeatMode = BeatMode.DEFAULT) {
    private val history = DoubleArray(HISTORY)
    private var count = 0
    private var write = 0
    private var sum = 0.0
    private var sumSq = 0.0
    private var lastBeatMs = NEVER

    fun update(nowMs: Long, energyDb: Double, sensitivity: Int): Boolean {
        var fire = false
        if (count >= MIN_HISTORY) {
            val mean = sum / count
            val variance = (sumSq / count - mean * mean).coerceAtLeast(0.0)
            val std = sqrt(variance).coerceAtLeast(MIN_STD_DB)
            val s = sensitivity.coerceIn(1, 10)
            val sinceLast = nowMs.toDouble() - lastBeatMs
            fire = energyDb >= MIC_ACTIVITY_DB && when (mode) {
                BeatMode.AGGRESSIVE -> {
                    val multiplier = 1.20 - (s - 1) * 0.075
                    sinceLast >= 95.0 &&
                        energyDb > mean + std * multiplier &&
                        energyDb - mean >= 2.0
                }
                BeatMode.PRECISION -> monotonic(
                    energyDb, mean, std, s, sinceLast,
                    baseMultiplier = 0.65, multiplierRange = 0.85,
                    baseRise = 2.0, riseRange = 2.0,
                    baseInterval = 150.0, intervalRange = 60.0,
                )
                BeatMode.HYBRID -> monotonic(
                    energyDb, mean, std, s, sinceLast,
                    baseMultiplier = 0.80, multiplierRange = 0.70,
                    baseRise = 2.0, riseRange = 1.0,
                    baseInterval = 120.0, intervalRange = 40.0,
                )
            }
            if (fire) lastBeatMs = nowMs.toDouble()
        }

        if (count == HISTORY) {
            val old = history[write]
            sum -= old
            sumSq -= old * old
        } else {
            count++
        }
        history[write] = energyDb
        write = (write + 1) % HISTORY
        sum += energyDb
        sumSq += energyDb * energyDb
        return fire
    }

    /** Monotonic sensitivity: 1 = easier, 10 = stronger evidence and longer spacing. */
    private fun monotonic(
        energyDb: Double, mean: Double, std: Double, s: Int, sinceLast: Double,
        baseMultiplier: Double, multiplierRange: Double,
        baseRise: Double, riseRange: Double,
        baseInterval: Double, intervalRange: Double,
    ): Boolean {
        val s01 = (s - 1) / 9.0
        return sinceLast >= baseInterval + s01 * intervalRange &&
            energyDb > mean + std * (baseMultiplier + s01 * multiplierRange) &&
            energyDb - mean >= baseRise + s01 * riseRange
    }

    companion object {
        private const val HISTORY = 256
        private const val MIN_HISTORY = 32
        private const val MIN_STD_DB = 0.8
        private const val MIC_ACTIVITY_DB = -52.0
        // Finite sentinel on purpose: Long.MIN_VALUE overflows on subtraction.
        private const val NEVER = -1.0e12
    }
}
