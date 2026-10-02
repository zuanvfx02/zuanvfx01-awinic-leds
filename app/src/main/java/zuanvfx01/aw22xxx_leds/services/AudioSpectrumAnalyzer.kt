package zuanvfx01.aw22xxx_leds.services

import kotlin.math.cos
import kotlin.math.ln1p
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lightweight FFT analysis for realtime LED control.
 *
 * The analyzer is fed a sliding window ([fftSize] samples) that advances by one
 * hop (256 samples) per call, so a frame is produced every ~5.3 ms at 48 kHz.
 *
 * spectralFlux is a band-weighted, log-compressed positive spectral flux. Each
 * band (bass / mid / high) is averaged on its own before being summed, so a
 * kick drum (only a handful of low bins) counts as much as a broadband snare.
 */
class AudioSpectrumAnalyzer(private val fftSize: Int = 1024) {
    data class Result(
        val rmsDb: Double,
        val bassRatio: Double,
        val centroidHz: Double,
        val peakHz: Double,
        val spectralFlux: Double,
    )

    private val real = DoubleArray(fftSize)
    private val imag = DoubleArray(fftSize)
    private val previousLog = DoubleArray(fftSize / 2)
    private val window = DoubleArray(fftSize) { i ->
        0.5 - 0.5 * cos(2.0 * Math.PI * i / (fftSize - 1))
    }

    // A full-scale sine gives a Hann-windowed peak magnitude of fftSize / 4.
    private val magnitudeScale = 4.0 / fftSize

    fun analyze(samples: ShortArray, size: Int, sampleRate: Int): Result {
        val n = minOf(size, fftSize)
        var sumSquares = 0.0
        for (i in 0 until fftSize) {
            val sample = if (i < n) samples[i].toDouble() / 32768.0 else 0.0
            if (i < n) sumSquares += sample * sample
            real[i] = sample * window[i]
            imag[i] = 0.0
        }
        fft()

        val bins = fftSize / 2
        val binHz = sampleRate.toDouble() / fftSize
        var total = 0.0
        var bass = 0.0
        var weighted = 0.0
        var peakMagnitude = 0.0
        var peakBin = 0
        var lowFlux = 0.0
        var midFlux = 0.0
        var highFlux = 0.0
        var lowCount = 0
        var midCount = 0
        var highCount = 0
        for (bin in 1 until bins) {
            val re = real[bin]
            val im = imag[bin]
            val magnitude = sqrt(re * re + im * im)
            val power = magnitude * magnitude
            val hz = bin * binHz
            total += power
            weighted += hz * power
            if (hz >= 40.0 && hz <= 250.0) bass += power
            if (magnitude > peakMagnitude) {
                peakMagnitude = magnitude
                peakBin = bin
            }

            val logMagnitude = ln1p(LOG_GAMMA * magnitude * magnitudeScale)
            val rise = logMagnitude - previousLog[bin]
            previousLog[bin] = logMagnitude
            if (hz >= LOW_MIN_HZ && hz < LOW_MAX_HZ) {
                lowCount++
                if (rise > 0.0) lowFlux += rise
            } else if (hz >= LOW_MAX_HZ && hz < MID_MAX_HZ) {
                midCount++
                if (rise > 0.0) midFlux += rise
            } else if (hz >= MID_MAX_HZ && hz < HIGH_MAX_HZ) {
                highCount++
                if (rise > 0.0) highFlux += rise
            }
        }

        val flux = LOW_WEIGHT * (lowFlux / lowCount.coerceAtLeast(1)) +
            MID_WEIGHT * (midFlux / midCount.coerceAtLeast(1)) +
            HIGH_WEIGHT * (highFlux / highCount.coerceAtLeast(1))

        val rms = sqrt(sumSquares / n.coerceAtLeast(1))
        return Result(
            rmsDb = if (rms > 1e-7) 20.0 * log10(rms) else -100.0,
            bassRatio = if (total > 1e-12) (bass / total).coerceIn(0.0, 1.0) else 0.0,
            centroidHz = if (total > 1e-12) (weighted / total).coerceIn(0.0, sampleRate / 2.0) else 0.0,
            peakHz = peakBin * binHz,
            spectralFlux = flux.coerceAtLeast(0.0),
        )
    }

    fun reset() {
        previousLog.fill(0.0)
    }

    private fun fft() {
        var j = 0
        for (i in 1 until fftSize) {
            var bit = fftSize shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= fftSize) {
            val angle = -2.0 * Math.PI / len
            val wLenR = cos(angle)
            val wLenI = sin(angle)
            var i = 0
            while (i < fftSize) {
                var wr = 1.0
                var wi = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * wr - imag[i + k + len / 2] * wi
                    val vI = real[i + k + len / 2] * wi + imag[i + k + len / 2] * wr
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR
                    imag[i + k + len / 2] = uI - vI
                    val nextWr = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR
                    wr = nextWr
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        private const val LOG_GAMMA = 300.0
        private const val LOW_MIN_HZ = 40.0
        private const val LOW_MAX_HZ = 250.0
        private const val MID_MAX_HZ = 2_000.0
        private const val HIGH_MAX_HZ = 8_000.0
        private const val LOW_WEIGHT = 1.0
        private const val MID_WEIGHT = 1.0
        private const val HIGH_WEIGHT = 0.6
    }
}
