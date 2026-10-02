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
 * Tempo-aware realtime beat tracker. Used by the PRECISION and HYBRID modes; the two differ
 * only by their [Profile]. ([Profile.PRECISION] is exactly the V6 behaviour.)
 *
 * Pipeline (one update() per 256-sample hop):
 *   log-spectral novelty
 *     -> adaptive threshold + relative-rise + peak guard = onset candidates
 *     -> autocorrelation of novelty history = tempo + confidence
 *     -> phase-locked beat grid = predicted beats when an onset is missing
 *     -> beat acceptance = tempo alignment OR a genuinely strong accent
 *
 * The important distinction is that an ONSET is now only a candidate. Once a reliable
 * tempo grid exists, weak/off-grid transients (hi-hat noise, speech consonants, reverb)
 * do not automatically become LED beats. Strong accents can still override the grid so
 * syncopated music remains responsive.
 *
 * Sensitivity is intentionally monotonic: 1 = easiest to trigger, 10 = strongest beats only.
 * The previous implementation accidentally inverted the threshold multiplier at high
 * sensitivity, which made 10/10 easier rather than harder.
 */
class TempoBeatTracker(
    private val frameMs: Double = 256.0 / 48_000.0 * 1000.0,
    private val profile: Profile = Profile.PRECISION,
) : BeatDetector {

    /**
     * Every knob that differs between PRECISION and HYBRID. Sensitivity is monotonic in both:
     * 1 = easiest to trigger, 10 = strongest beats only.
     */
    data class Profile(
        // Adaptive threshold: mean + std * (lowSigma .. lowSigma + sigmaRange).
        val lowSigma: Double,
        val sigmaRange: Double,
        // Onset must exceed mean flux by (relRiseBase .. relRiseBase + relRiseRange).
        val relRiseBase: Double,
        val relRiseRange: Double,
        // Beat spacing.
        val minBeatGapMs: Double,
        val maxExtraGapMs: Double,
        val minBeatFraction: Double,
        // Before tempo lock: sensitivity <= preLockOpenUpTo accepts every onset.
        val preLockOpenUpTo: Int,
        val preLockRatioBase: Double,
        val preLockRatioRange: Double,
        // What counts as a strong accent (may override the tempo grid).
        val strongRatioBase: Double,
        val strongRatioRange: Double,
        val strongRiseBase: Double,
        val strongRiseRange: Double,
        /** >0: a strong accent may fire after only this gap even when the tempo grid is locked. */
        val strongAccentGapMs: Double,
        // Tempo lock requirements and prediction window.
        val minLockConfidence: Double,
        val minLockQuality: Double,
        /**
         * Once locked, the lock is only released when confidence/quality fall below
         * `requirement * (1 - lockHysteresis)`. 0 = no hysteresis (V6 behaviour).
         */
        val lockHysteresis: Double,
        /** Lock-quality loss per off-grid onset while locked. */
        val lockPenalty: Double,
        val predictWaitMs: Double,
        val predictMaxLateMs: Double,
    ) {
        companion object {
            /** Tempo-first: identical to the V6 detector. */
            val PRECISION = Profile(
                lowSigma = 0.95, sigmaRange = 1.05,
                relRiseBase = 1.15, relRiseRange = 0.99,
                minBeatGapMs = 140.0, maxExtraGapMs = 70.0, minBeatFraction = 0.28,
                preLockOpenUpTo = 4, preLockRatioBase = 1.02, preLockRatioRange = 0.18,
                strongRatioBase = 1.65, strongRatioRange = 0.15,
                strongRiseBase = 1.8, strongRiseRange = 0.45,
                strongAccentGapMs = 0.0,
                minLockConfidence = 0.65, minLockQuality = 0.62,
                lockHysteresis = 0.0, lockPenalty = 0.10,
                predictWaitMs = 8.0, predictMaxLateMs = 35.0,
            )

            /**
             * Tempo grid + fast transients. Onset thresholds are the same as PRECISION (easier
             * onsets let hi-hats pull the beat grid off the kick), but the grid locks earlier
             * and stays locked longer (hysteresis), spacing is shorter, and a strong accent
             * qualifies more easily and may fire off-grid after a short gap.
             */
            val HYBRID = Profile(
                lowSigma = 0.95, sigmaRange = 1.05,
                relRiseBase = 1.15, relRiseRange = 0.99,
                minBeatGapMs = 120.0, maxExtraGapMs = 55.0, minBeatFraction = 0.26,
                preLockOpenUpTo = 5, preLockRatioBase = 1.02, preLockRatioRange = 0.16,
                strongRatioBase = 1.50, strongRatioRange = 0.15,
                strongRiseBase = 1.65, strongRiseRange = 0.40,
                strongAccentGapMs = 95.0,
                minLockConfidence = 0.55, minLockQuality = 0.52,
                lockHysteresis = 0.25, lockPenalty = 0.03,
                predictWaitMs = 10.0, predictMaxLateMs = 40.0,
            )
        }
    }

    // ~1 second adaptive onset history.
    private val historyFrames = (1000.0 / frameMs).roundToInt().coerceIn(32, 256)
    private val fluxHistory = DoubleArray(historyFrames)
    private var historySize = 0
    private var historyWrite = 0

    // ~6 seconds of onset novelty for tempo estimation.
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

    private var previousFlux = 0.0
    private var lastOnsetMs = NEVER
    private var lastBeatMs = NEVER
    private var nextPredictedMs = Double.NaN
    private var missedBeats = 0
    private var wasLocked = false

    override fun update(nowMs: Long, analysis: AudioSpectrumAnalyzer.Result, sensitivity: Int): BeatTrackResult {
        frames++
        val now = nowMs.toDouble()
        val flux = analysis.spectralFlux
        val s = sensitivity.coerceIn(1, 10)
        val sensitivity01 = (s - 1) / 9.0

        val mean = average(fluxHistory, historySize)
        val std = standardDeviation(fluxHistory, historySize, mean)
        val threshold = adaptiveThreshold(s, mean, std)
        val normalizedFlux = if (threshold > 1e-9) flux / threshold else 0.0

        // Higher sensitivity number means stronger evidence is required.
        val relativeRise = profile.relRiseBase + sensitivity01 * profile.relRiseRange
        val peakRatio = PEAK_RATIO_BASE + sensitivity01 * PEAK_RATIO_RANGE
        val energyGate = analysis.rmsDb >= ENERGY_GATE_DB
        val refractory = now - lastOnsetMs >= MIN_ONSET_GAP_MS
        val risingEnough = flux >= max(FLUX_FLOOR, previousFlux * peakRatio)
        val onset = energyGate && refractory &&
            flux > threshold &&
            normalizedFlux >= MIN_FLUX_RATIO &&
            flux >= mean * relativeRise &&
            risingEnough

        // Feed the tempo estimator with novelty, not the raw positive flux floor. This makes
        // periodic accents stand out while reducing the influence of a constant loud signal.
        val novelty = max(0.0, flux - mean)
        pushFlux(flux)
        pushOdf(novelty)
        previousFlux = flux
        if (frames % TEMPO_EVERY_FRAMES == 0) updateTempo()

        val confidence = tempoConfidence * (0.5 + 0.5 * lockQuality)
        val period = periodMs
        // Hysteresis (HYBRID): easier to stay locked than to become locked, so a few off-grid
        // transients cannot flip the detector back to "accept every onset" mid-song.
        val release = if (wasLocked) 1.0 - profile.lockHysteresis else 1.0
        val tempoLocked = period > 0.0 &&
            confidence >= profile.minLockConfidence * release &&
            lockQuality >= profile.minLockQuality * release
        wasLocked = tempoLocked
        var beat = false
        var predicted = false
        var strength = (normalizedFlux - 1.0).coerceIn(0.0, 2.0) / 2.0

        if (onset) {
            val aligned = if (period > 0.0 && !nextPredictedMs.isNaN()) {
                val error = now - nextPredictedMs
                val wrapped = error - Math.rint(error / period) * period
                abs(wrapped) <= max(MIN_LOCK_WINDOW_MS, period * LOCK_WINDOW_FRACTION)
            } else {
                false
            }

            val strongAccent = normalizedFlux >= (profile.strongRatioBase + profile.strongRatioRange * sensitivity01) &&
                flux >= mean * (profile.strongRiseBase + profile.strongRiseRange * sensitivity01)

            // Before tempo lock, keep low sensitivities responsive. At high sensitivities
            // require a stronger accent so random transients don't become beats.
            val preLockAccepted = if (sensitivity <= profile.preLockOpenUpTo) {
                true
            } else {
                normalizedFlux >= (profile.preLockRatioBase + profile.preLockRatioRange * sensitivity01)
            }

            var minBeatGap = if (tempoLocked) {
                max(profile.minBeatGapMs, period * profile.minBeatFraction)
            } else {
                profile.minBeatGapMs + (profile.maxExtraGapMs * sensitivity01)
            }
            // HYBRID only: a genuinely strong transient does not have to wait out the beat grid.
            if (strongAccent && profile.strongAccentGapMs > 0.0) {
                minBeatGap = min(minBeatGap, profile.strongAccentGapMs)
            }

            val accepted = if (tempoLocked) {
                (aligned || strongAccent) && now - lastBeatMs >= minBeatGap
            } else {
                preLockAccepted && now - lastBeatMs >= minBeatGap
            }

            if (period > 0.0) {
                if (nextPredictedMs.isNaN()) {
                    nextPredictedMs = now + period
                } else {
                    val error = now - nextPredictedMs
                    val wrapped = error - Math.rint(error / period) * period
                    if (abs(wrapped) <= max(MIN_LOCK_WINDOW_MS, period * LOCK_WINDOW_FRACTION)) {
                        // Soft correction: move the grid toward a detected beat.
                        nextPredictedMs += wrapped * PHASE_GAIN
                        lockQuality += (1.0 - lockQuality) * LOCK_SMOOTHING
                        missedBeats = 0
                    } else if (tempoLocked) {
                        lockQuality *= (1.0 - profile.lockPenalty)
                    }
                }
            }

            if (accepted) {
                beat = true
                lastBeatMs = now
                strength = max(strength, if (strongAccent) 0.72 else 0.45)
            }
            lastOnsetMs = now
        } else if (tempoLocked && energyGate && !nextPredictedMs.isNaN() &&
            now >= nextPredictedMs + profile.predictWaitMs &&
            now <= nextPredictedMs + profile.predictMaxLateMs &&
            missedBeats < MAX_MISSED &&
            now - lastBeatMs >= max(profile.minBeatGapMs, period * profile.minBeatFraction)
        ) {
            // Fill only the grid point that is currently due. The previous implementation
            // could fire a prediction long after a bad phase estimate, producing a beat in
            // the middle of two real beats. A narrow late window keeps prediction musical.
            beat = true
            predicted = true
            strength = max(strength, confidence * 0.55)
            lastBeatMs = now
            missedBeats++
            while (nextPredictedMs <= now) nextPredictedMs += period
        } else if (tempoLocked && !nextPredictedMs.isNaN() &&
            now > nextPredictedMs + period * PREDICT_MAX_SKIP_FRACTION
        ) {
            // We missed the prediction window; advance the grid without emitting a false beat.
            while (nextPredictedMs <= now) nextPredictedMs += period
            missedBeats = min(MAX_MISSED, missedBeats + 1)
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
        previousFlux = 0.0
        lastOnsetMs = NEVER
        lastBeatMs = NEVER
        nextPredictedMs = Double.NaN
        missedBeats = 0
        wasLocked = false
    }

    private fun adaptiveThreshold(sensitivity: Int, mean: Double, std: Double): Double {
        if (historySize < MIN_HISTORY) return max(FLUX_FLOOR, mean * INITIAL_MEAN_MULTIPLIER)
        val s01 = (sensitivity.coerceIn(1, 10) - 1) / 9.0
        // PRECISION: 0.95 sigma at 1/10 -> 2.0 sigma at 10/10. Monotonic by design.
        val sigmaMultiplier = profile.lowSigma + s01 * profile.sigmaRange
        return max(FLUX_FLOOR, mean + std * sigmaMultiplier)
    }

    private fun standardDeviation(values: DoubleArray, size: Int, mean: Double): Double {
        if (size <= 1) return 0.0
        var variance = 0.0
        for (i in 0 until size) {
            val d = values[i] - mean
            variance += d * d
        }
        return kotlin.math.sqrt(variance / size).coerceAtLeast(1e-6)
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

    /** Autocorrelation tempo estimate with a broad tempo prior rather than forcing 120 BPM. */
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

        val contrast = ((acf[bestLag] - meanAcf) * TEMPO_CONTRAST_GAIN).coerceIn(0.0, 1.0)
        tempoConfidence = tempoConfidence * TEMPO_CONF_DECAY + contrast * (1.0 - TEMPO_CONF_DECAY)

        val candidate = fractionalLag * frameMs
        if (periodMs <= 0.0) {
            periodMs = candidate
        } else if (abs(candidate / periodMs - 1.0) < PERIOD_TOLERANCE) {
            periodMs += (candidate - periodMs) * 0.25
            pendingCount = 0
        } else {
            // A different tempo or octave must win several estimates in a row.
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
                lockQuality = 0.0
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
        return exp(-0.5 * (octaves / TEMPO_PRIOR_WIDTH_OCTAVES) * (octaves / TEMPO_PRIOR_WIDTH_OCTAVES))
    }

    private fun average(values: DoubleArray, size: Int): Double {
        if (size <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until size) sum += values[i]
        return sum / size
    }

    companion object {
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
        private const val INITIAL_MEAN_MULTIPLIER = 1.6
        private const val ENERGY_GATE_DB = -52.0

        // Raw onset candidate spacing. This is deliberately below a typical musical beat
        // interval but high enough to reject rapid chatter from the same transient.
        private const val MIN_ONSET_GAP_MS = 85.0
        private const val MIN_FLUX_RATIO = 1.02
        private const val PEAK_RATIO_BASE = 1.00
        private const val PEAK_RATIO_RANGE = 0.12

        private const val MIN_LOCK_WINDOW_MS = 45.0
        private const val LOCK_WINDOW_FRACTION = 0.18
        private const val PHASE_GAIN = 0.35
        private const val LOCK_SMOOTHING = 0.18
        private const val PREDICT_MAX_SKIP_FRACTION = 0.35
        private const val MAX_MISSED = 3

        private const val TEMPO_CONTRAST_GAIN = 2.5
        private const val TEMPO_CONF_DECAY = 0.55
        private const val TEMPO_PRIOR_WIDTH_OCTAVES = 1.35
    }
}
