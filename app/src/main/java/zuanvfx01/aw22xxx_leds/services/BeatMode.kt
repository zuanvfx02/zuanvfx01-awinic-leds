package zuanvfx01.aw22xxx_leds.services

/**
 * Beat detection modes selectable in Music LED settings.
 *
 * [id] is what is stored in `MusicLedConfig.beat_mode`. Id 0 is intentionally AGGRESSIVE:
 * proto3 returns 0 for a field that was never written, so every existing install (and every
 * fresh install) keeps the original V5-style behaviour without a migration.
 */
enum class BeatMode(
    val id: Int,
    /** How long the spectral tracker must be silent before the energy fallback may fire. */
    val legacyFallbackMs: Long,
    /** The energy fallback is blocked while tempo confidence is at or above this value. */
    val fallbackMaxTempoConfidence: Double,
) {
    /** Original V5 detector: every spectral onset is a beat. Fast, reacts to everything. */
    AGGRESSIVE(0, 1_200L, Double.POSITIVE_INFINITY),

    /** V6 detector: onset is only a candidate, a locked tempo grid vetoes off-grid noise. */
    PRECISION(1, 1_400L, 0.48),

    /** V6 tempo grid, but strong off-grid accents (kick/snare) fire immediately. */
    HYBRID(2, 1_300L, 0.55);

    companion object {
        val DEFAULT = AGGRESSIVE
        fun fromId(id: Int): BeatMode = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
