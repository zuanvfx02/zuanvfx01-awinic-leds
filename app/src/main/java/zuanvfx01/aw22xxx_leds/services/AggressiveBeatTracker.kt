package zuanvfx01.aw22xxx_leds.services

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * AGGRESSIVE mode: the original V5-style realtime beat tracker, kept unchanged in behaviour.
 * (Only the class name and the shared [BeatTrackResult]/[BeatDetector] types changed.)
 *
 * Note: in this mode a HIGHER sensitivity value lowers the threshold (reacts to more).
 *
 * Pipeline (one update() per 256-sample hop):
 *   onset strength (band-weighted log spectral flux)
 *     -> adaptive threshold + relative-rise guard   = onsets (LED fires immediately)
 *     -> autocorrelation of ~6 s of onset strength  = tempo (BPM) + confidence
 *     -> soft phase-locked loop on the onsets       = predicted beat grid
 *
 * Onsets always produce a beat (low latency). The predicted grid only fills in
 * beats whose onset was missed, and stops after [MAX_MISSED] misses in a row.
 *
 * IMPORTANT: [frameMs] must be the real hop duration (hop samples / sample
 * rate). All timestamps are Doubles with finite "never" sentinels; Long.MIN_VALUE
 * sentinels overflow on subtraction and silently disable the refractory checks.
 */
class AggressiveBeatTracker(
    private val frameMs: Double = 512.0 / 48_000.0 * 1000.0,
) : BeatDetector {

    // Adaptive-threshold history (~1 s).
    private val historyFrames = (1000.0 / frameMs).roundToInt().coerceIn(32, 256)
    private val fluxHistory = DoubleArray(historyFrames)
    private var historySize = 0
    private var historyWrite = 0

    // Tempo analysis (~6 s of onset strength).
    private val odfFrames = (TEMPO_WINDOW_MS / frameMs).roundToInt()
    private val odfRing = DoubleArray(odfFrames)
    private val work = DoubleArray(odfFrames)
    private var odfCount = 0
    private var odfWrite = 0
    private val minLag = ceil(MIN_PERIOD_MS / frameMs).toInt()
    private val maxLag = floor(MAX_PERIOD_MS / frameMs).toInt()
    private val acf = DoubleArray(maxLag * 2 + 2)

    private var frames = 0
    private var periodMs = 0.0
    private var bpm = 0.0
    private var tempoConfidence = 0.0
    private var lockQuality = 0.0
    private var pendingPeriodMs = 0.0
    private var pendingCount = 0

    private var lastOnsetMs = NEVER
    private var lastBeatMs = NEVER
    private var nextPredictedMs = Double.NaN
    private var missedBeats = 0

    override fun update(nowMs: Long, analysis: AudioSpectrumAnalyzer.Result, sensitivity: Int): BeatTrackResult {
        frames++
        val now = nowMs.toDouble()
        val flux = analysis.spectralFlux
        val s = sensitivity.coerceIn(1, 10)

        val mean = average(fluxHistory, historySize)
        val threshold = adaptiveThreshold(sensitivity, mean)
        val normalizedFlux = if (threshold > 1e-9) flux / threshold else 0.0
        val relativeRise = REL_RISE_BASE - (s - 1) * REL_RISE_STEP
        val energyGate = analysis.rmsDb >= ENERGY_GATE_DB
        val refractory = now - lastOnsetMs >= MIN_ONSET_GAP_MS
        val onset = energyGate && refractory &&
            flux > threshold &&
            normalizedFlux >= MIN_FLUX_RATIO &&
            flux >= mean * relativeRise

        pushFlux(flux)
        pushOdf(flux)
        if (frames % TEMPO_EVERY_FRAMES == 0) updateTempo()

        val confidence = tempoConfidence * (0.5 + 0.5 * lockQuality)
        val period = periodMs
        val confident = period > 0.0 && confidence >= MIN_PREDICTION_CONFIDENCE

        var beat = false
        var predicted = false
        var strength = (normalizedFlux - 1.0).coerceIn(0.0, 2.0) / 2.0

        if (onset) {
            if (period > 0.0) {
                if (nextPredictedMs.isNaN()) {
                    nextPredictedMs = now + period
                } else {
                    val error = now - nextPredictedMs
                    val wrapped = error - Math.rint(error / period) * period
                    if (abs(wrapped) <= max(MIN_LOCK_WINDOW_MS, period * LOCK_WINDOW_FRACTION)) {
                        // Soft correction: move the grid part of the way to the onset.
                        nextPredictedMs += wrapped * PHASE_GAIN
                        lockQuality += (1.0 - lockQuality) * LOCK_SMOOTHING
                        missedBeats = 0
                    } else {
                        lockQuality -= lockQuality * LOCK_SMOOTHING
                    }
                }
            }
            if (now - lastBeatMs >= MIN_BEAT_GAP_MS) {
                beat = true
                lastBeatMs = now
            }
            lastOnsetMs = now
        } else if (confident && energyGate && !nextPredictedMs.isNaN() &&
            now >= nextPredictedMs + PREDICT_WAIT_MS &&
            missedBeats < MAX_MISSED &&
            now - lastBeatMs >= MIN_BEAT_GAP_MS
        ) {
            // The onset for this grid point never showed up: fill the beat in.
            beat = true
            predicted = true
            strength = max(strength, confidence * 0.55)
            lastBeatMs = now
            missedBeats++
            while (nextPredictedMs <= now) nextPredictedMs += period
        }

        var phase = 0.0
        if (period > 0.0 && !nextPredictedMs.isNaN()) {
            while (nextPredictedMs <= now - period) nextPredictedMs += period
            phase = (1.0 - (nextPredictedMs - now) / period).coerceIn(0.0, 1.0)
        }

        return BeatTrackResult(beat, onset, predicted, bpm, confidence, phase, strength, flux, threshold)
    }

    override fun reset() {
        fluxHistory.fill(0.0)
        odfRing.fill(0.0)
        historySize = 0
        historyWrite = 0
        odfCount = 0
        odfWrite = 0
        frames = 0
        periodMs = 0.0
        bpm = 0.0
        tempoConfidence = 0.0
        lockQuality = 0.0
        pendingPeriodMs = 0.0
        pendingCount = 0
        lastOnsetMs = NEVER
        lastBeatMs = NEVER
        nextPredictedMs = Double.NaN
        missedBeats = 0
    }

    private fun adaptiveThreshold(sensitivity: Int, mean: Double): Double {
        if (historySize < MIN_HISTORY) return max(FLUX_FLOOR, mean * 1.6)
        var variance = 0.0
        for (i in 0 until historySize) {
            val d = fluxHistory[i] - mean
            variance += d * d
        }
        val std = kotlin.math.sqrt(variance / historySize).coerceAtLeast(1e-6)
        val s = sensitivity.coerceIn(1, 10)
        val multiplier = 1.65 - (s - 1) * 0.075
        return max(FLUX_FLOOR, mean + std * multiplier)
    }

    private fun pushFlux(flux: Double) {
        fluxHistory[historyWrite] = flux
        historyWrite = (historyWrite + 1) % historyFrames
        historySize = min(historyFrames, historySize + 1)
    }

    private fun pushOdf(flux: Double) {
        odfRing[odfWrite] = flux
        odfWrite = (odfWrite + 1) % odfFrames
        odfCount = min(odfFrames, odfCount + 1)
    }

    /** Autocorrelation tempo estimate with half-tempo comb support and a mild 120 BPM prior. */
    private fun updateTempo() {
        val n = min(odfCount, odfFrames)
        if (n < (MIN_TEMPO_WINDOW_MS / frameMs).toInt()) return

        val start = (odfWrite - n + odfFrames) % odfFrames
        var sum = 0.0
        for (i in 0 until n) {
            val v = odfRing[(start + i) % odfFrames]
            work[i] = v
            sum += v
        }
        val mean = sum / n
        var r0 = 0.0
        for (i in 0 until n) {
            val x = max(0.0, work[i] - mean)
            work[i] = x
            r0 += x * x
        }
        if (r0 < 1e-12) return

        val maxComputed = min(maxLag * 2, n / 2)
        val searchMax = min(maxLag, maxComputed)
        if (searchMax <= minLag + 2) return

        val norm = n / r0
        for (lag in minLag..maxComputed) {
            var acc = 0.0
            for (t in lag until n) acc += work[t] * work[t - lag]
            acf[lag] = acc / (n - lag) * norm
        }

        var bestLag = -1
        var bestScore = -1.0
        var meanAcf = 0.0
        for (lag in minLag..searchMax) {
            meanAcf += acf[lag]
            val score = combScore(lag, maxComputed) * tempoPrior(lag)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag < 0) return
        meanAcf /= (searchMax - minLag + 1)

        var fractionalLag = bestLag.toDouble()
        if (bestLag > minLag && bestLag < searchMax) {
            val a = combScore(bestLag - 1, maxComputed)
            val b = combScore(bestLag, maxComputed)
            val c = combScore(bestLag + 1, maxComputed)
            val denominator = a - 2.0 * b + c
            if (abs(denominator) > 1e-9) {
                fractionalLag = (bestLag + 0.5 * (a - c) / denominator)
                    .coerceIn(bestLag - 1.0, bestLag + 1.0)
            }
        }

        val contrast = ((acf[bestLag] - meanAcf) * 2.5).coerceIn(0.0, 1.0)
        tempoConfidence = tempoConfidence * 0.5 + contrast * 0.5

        val candidate = fractionalLag * frameMs
        if (periodMs <= 0.0) {
            periodMs = candidate
        } else if (abs(candidate / periodMs - 1.0) < PERIOD_TOLERANCE) {
            periodMs += (candidate - periodMs) * 0.3
            pendingCount = 0
        } else {
            // A different tempo (or octave) must win several estimates in a row.
            if (pendingPeriodMs > 0.0 && abs(candidate / pendingPeriodMs - 1.0) < PERIOD_TOLERANCE) {
                pendingCount++
            } else {
                pendingPeriodMs = candidate
                pendingCount = 1
            }
            if (pendingCount >= PERIOD_SWITCH_COUNT) {
                periodMs = candidate
                pendingCount = 0
                nextPredictedMs = Double.NaN
                missedBeats = 0
            }
        }
        bpm = 60_000.0 / periodMs
    }

    private fun combScore(lag: Int, maxComputed: Int): Double {
        val harmonic = if (lag * 2 <= maxComputed) 0.5 * acf[lag * 2] else 0.0
        return acf[lag] + harmonic
    }

    private fun tempoPrior(lag: Int): Double {
        val tempo = 60_000.0 / (lag * frameMs)
        val octaves = ln(tempo / 120.0) / ln(2.0)
        return exp(-0.5 * (octaves / 0.9) * (octaves / 0.9))
    }

    private fun average(values: DoubleArray, size: Int): Double {
        if (size <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until size) sum += values[i]
        return sum / size
    }

    companion object {
        // Finite sentinel on purpose: Long.MIN_VALUE overflows on subtraction.
        private const val NEVER = -1.0e12

        private const val TEMPO_WINDOW_MS = 6_000.0
        private const val MIN_TEMPO_WINDOW_MS = 3_000.0
        private const val TEMPO_EVERY_FRAMES = 12
        private const val MIN_PERIOD_MS = 300.0 // 200 BPM
        private const val MAX_PERIOD_MS = 1_000.0 // 60 BPM
        private const val PERIOD_TOLERANCE = 0.06
        private const val PERIOD_SWITCH_COUNT = 3

        private const val MIN_HISTORY = 12
        private const val FLUX_FLOOR = 0.05
        private const val ENERGY_GATE_DB = -52.0
        private const val MIN_ONSET_GAP_MS = 85.0
        private const val MIN_BEAT_GAP_MS = 90.0
        private const val MIN_FLUX_RATIO = 1.08
        // Onset must exceed mean flux by this factor; lowered by the sensitivity slider
        // (3.0 at sensitivity 1 down to ~1.9 at 10). Keeps steady noise from firing.
        private const val REL_RISE_BASE = 3.0
        private const val REL_RISE_STEP = 0.12

        private const val MIN_PREDICTION_CONFIDENCE = 0.42
        private const val MIN_LOCK_WINDOW_MS = 40.0
        private const val LOCK_WINDOW_FRACTION = 0.15
        private const val PHASE_GAIN = 0.4
        private const val LOCK_SMOOTHING = 0.15
        private const val PREDICT_WAIT_MS = 25.0
        private const val MAX_MISSED = 3
    }
}
