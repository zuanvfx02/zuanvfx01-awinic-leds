package zuanvfx01.aw22xxx_leds.services

/** One analysis hop worth of beat-tracking output. Shared by every [BeatMode]. */
data class BeatTrackResult(
    val beat: Boolean,
    /** Raw spectral onset candidate. In tempo-aware modes an onset is not automatically a beat. */
    val onset: Boolean,
    val predicted: Boolean,
    val bpm: Double,
    val confidence: Double,
    val phase: Double,
    val strength: Double,
    /** Raw onset strength of this frame and the adaptive threshold it was compared to. */
    val flux: Double,
    val threshold: Double,
)

/** Common surface of the three trackers so the service can swap modes at runtime. */
interface BeatDetector {
    fun update(nowMs: Long, analysis: AudioSpectrumAnalyzer.Result, sensitivity: Int): BeatTrackResult
    fun reset()
}

object BeatDetectors {
    /** Builds the tracker for [mode]. [frameMs] must be the real hop duration. */
    fun create(mode: BeatMode, frameMs: Double): BeatDetector = when (mode) {
        BeatMode.AGGRESSIVE -> AggressiveBeatTracker(frameMs)
        BeatMode.PRECISION -> TempoBeatTracker(frameMs, TempoBeatTracker.Profile.PRECISION)
        BeatMode.HYBRID -> TempoBeatTracker(frameMs, TempoBeatTracker.Profile.HYBRID)
    }

    fun createLegacy(mode: BeatMode): LegacyEnergyDetector = LegacyEnergyDetector(mode)
}
