package zuanvfx01.aw22xxx_leds.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Live, in-process telemetry of the Music LED detector, consumed by the dashboard UI.
 *
 * The audio loop calls [onHop] once per 256-sample hop (~5.3 ms at 48 kHz). Samples are folded into
 * ~33 ms ticks (peak-hold, so short beats are never lost between ticks) and published as an
 * immutable [Snapshot]. When nothing is collecting [state] (app in background / dashboard not
 * on screen) [onHop] returns immediately, so the dashboard costs no battery while hidden.
 *
 * Thread safety: the audio loop (Dispatchers.Default) and the LED worker (Dispatchers.IO)
 * both write here, so every mutator is @Synchronized.
 */
object MusicTelemetry {
    const val HISTORY = 96            // ~3.2 s of history at one tick per 33 ms
    const val LED_COUNT = 6           // LEDs drawn in the dashboard stack
    const val NO_SIGNAL_DB = -100f

    private const val PUBLISH_INTERVAL_MS = 33L
    private const val PULSE_HALF_LIFE_MS = 140.0
    private const val MAX_EVENTS = 6

    enum class BeatSource { ONSET, PREDICTED, FALLBACK }

    /** One real LED change (effect/frequency/color write) caused by a beat. */
    data class LedEvent(
        val atMs: Long,
        val source: BeatSource,
        val ratio: Float,       // onset strength / adaptive threshold at the trigger
        val levelDb: Float,
        val effect: Int,
        val frequencyHz: Int,
        val detectedAtMs: Long = atMs,
        val deliveryLatencyMs: Long = 0L,
    )

    /** Tiny, rarely-changing summary for screens that only need "is it on / which BPM". */
    data class Summary(val running: Boolean = false, val bpm: Int = 0)

    /** Immutable frame. Arrays are oldest -> newest and must never be mutated by readers. */
    class Snapshot(
        val running: Boolean = false,
        val active: Boolean = false,
        val levelDb: Float = NO_SIGNAL_DB,
        val gateDb: Float = -52f,
        val levelHistory: FloatArray = FloatArray(HISTORY) { NO_SIGNAL_DB },
        val fluxHistory: FloatArray = FloatArray(HISTORY),
        val thresholdHistory: FloatArray = FloatArray(HISTORY),
        /** 0 = none, 1 = onset, 2 = predicted, 3 = fallback energy detector. */
        val beatHistory: ByteArray = ByteArray(HISTORY),
        val flux: Float = 0f,
        val threshold: Float = 0f,
        val bpm: Float = 0f,
        val confidence: Float = 0f,
        val phase: Float = 0f,
        /** 0..1 height of the LED stack: loudness floor + decaying beat pulse. */
        val ledLevel: Float = 0f,
        /** ARGB of the LED colors the service last wrote (empty = unknown). */
        val ledColors: IntArray = IntArray(0),
        val beatCount: Int = 0,
        val events: List<LedEvent> = emptyList(),
        val nowMs: Long = 0L,
    ) {
        val ratio: Float get() = if (threshold > 1e-6f) flux / threshold else 0f
        val lastEventAtMs: Long get() = events.firstOrNull()?.atMs ?: Long.MIN_VALUE / 2
    }

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private val _summary = MutableStateFlow(Summary())
    val summary: StateFlow<Summary> = _summary.asStateFlow()

    // ---- mutable recorder state (guarded by the object monitor) ----
    private val levelHist = FloatArray(HISTORY) { NO_SIGNAL_DB }
    private val fluxHist = FloatArray(HISTORY)
    private val thrHist = FloatArray(HISTORY)
    private val beatHist = ByteArray(HISTORY)

    private var running = false
    private var gateDb = -52f
    private var lastHopMs = 0L
    private var lastPublishMs = 0L
    private var pulse = 0.0
    private var beatCount = 0
    private var events: List<LedEvent> = emptyList()
    private var ledColors: IntArray = IntArray(0)

    private var accLevel = NO_SIGNAL_DB
    private var accFlux = 0f
    private var accThr = 0f
    private var accBeat: Byte = 0

    private var lastActive = false
    private var lastLevel = NO_SIGNAL_DB
    private var lastFlux = 0f
    private var lastThr = 0f
    private var lastBpm = 0f
    private var lastConf = 0f
    private var lastPhase = 0f
    private var lastLedLevel = 0f

    @Synchronized
    fun onSessionStart(gateDb: Float) {
        resetRecorder()
        this.gateDb = gateDb
        running = true
        publish(force = true, nowMs = 0L)
        updateSummary()
    }

    @Synchronized
    fun onSessionEnd() {
        if (!running && _state.value.running.not()) return
        running = false
        resetRecorder()
        publish(force = true, nowMs = 0L)
        updateSummary()
    }

    /** Colors the service last wrote to the LED (ARGB, index = hardware LED index). */
    @Synchronized
    fun publishColors(argb: IntArray) {
        ledColors = argb.copyOf()
    }

    /** Called when the LED worker actually changed the LED because of a beat. */
    @Synchronized
    fun onLedChange(event: LedEvent) {
        events = (listOf(event) + events).take(MAX_EVENTS)
    }

    @Synchronized
    fun onHop(
        nowMs: Long,
        levelDb: Double,
        active: Boolean,
        flux: Double,
        threshold: Double,
        bpm: Double,
        confidence: Double,
        phase: Double,
        beat: BeatSource?,
        strength: Double,
    ) {
        if (!running) return
        // Nobody is looking at the dashboard: do no work at all.
        if (_state.subscriptionCount.value == 0 && _summary.subscriptionCount.value == 0) return

        val dt = if (lastHopMs == 0L) 0.0 else (nowMs - lastHopMs).toDouble().coerceIn(0.0, 100.0)
        lastHopMs = nowMs

        pulse *= 0.5.pow(dt / PULSE_HALF_LIFE_MS)
        if (beat != null) {
            beatCount++
            val peak = (0.62 + 0.38 * strength.coerceIn(0.0, 1.0)).let {
                if (beat == BeatSource.PREDICTED) it * 0.85 else it
            }
            pulse = max(pulse, peak)
        }
        val loudness = ((levelDb + 60.0) / 50.0).coerceIn(0.0, 1.0)
        val ledLevel = if (active) max(pulse, loudness * 0.5) else pulse

        accLevel = max(accLevel, levelDb.toFloat())
        accFlux = max(accFlux, flux.toFloat())
        accThr = threshold.toFloat()
        if (beat != null) {
            val code: Byte = when (beat) {
                BeatSource.ONSET -> 1
                BeatSource.PREDICTED -> 2
                BeatSource.FALLBACK -> 3
            }
            accBeat = if (accBeat == 0.toByte() || code < accBeat) code else accBeat
        }

        lastActive = active
        lastLevel = levelDb.toFloat()
        lastFlux = flux.toFloat()
        lastThr = threshold.toFloat()
        lastBpm = bpm.toFloat()
        lastConf = confidence.toFloat()
        lastPhase = phase.toFloat()
        lastLedLevel = ledLevel.toFloat()

        if (nowMs - lastPublishMs >= PUBLISH_INTERVAL_MS) {
            pushTick()
            publish(force = false, nowMs = nowMs)
            lastPublishMs = nowMs
            updateSummary()
        }
    }

    // ---- internals ----

    private fun pushTick() {
        System.arraycopy(levelHist, 1, levelHist, 0, HISTORY - 1)
        System.arraycopy(fluxHist, 1, fluxHist, 0, HISTORY - 1)
        System.arraycopy(thrHist, 1, thrHist, 0, HISTORY - 1)
        System.arraycopy(beatHist, 1, beatHist, 0, HISTORY - 1)
        levelHist[HISTORY - 1] = accLevel
        fluxHist[HISTORY - 1] = accFlux
        thrHist[HISTORY - 1] = accThr
        beatHist[HISTORY - 1] = accBeat
        accLevel = NO_SIGNAL_DB
        accFlux = 0f
        accBeat = 0
    }

    private fun publish(force: Boolean, nowMs: Long) {
        if (!force && _state.subscriptionCount.value == 0) return
        _state.value = Snapshot(
            running = running,
            active = lastActive,
            levelDb = lastLevel,
            gateDb = gateDb,
            levelHistory = levelHist.copyOf(),
            fluxHistory = fluxHist.copyOf(),
            thresholdHistory = thrHist.copyOf(),
            beatHistory = beatHist.copyOf(),
            flux = lastFlux,
            threshold = lastThr,
            bpm = lastBpm,
            confidence = lastConf,
            phase = lastPhase,
            ledLevel = lastLedLevel,
            ledColors = ledColors,
            beatCount = beatCount,
            events = events,
            nowMs = nowMs,
        )
    }

    private fun updateSummary() {
        val bpm = if (running && lastConf >= 0.42f && lastBpm > 0f) lastBpm.roundToInt() else 0
        _summary.value = Summary(running = running, bpm = bpm)
    }

    private fun resetRecorder() {
        levelHist.fill(NO_SIGNAL_DB)
        fluxHist.fill(0f)
        thrHist.fill(0f)
        beatHist.fill(0)
        lastHopMs = 0L
        lastPublishMs = 0L
        pulse = 0.0
        beatCount = 0
        events = emptyList()
        accLevel = NO_SIGNAL_DB
        accFlux = 0f
        accThr = 0f
        accBeat = 0
        lastActive = false
        lastLevel = NO_SIGNAL_DB
        lastFlux = 0f
        lastThr = 0f
        lastBpm = 0f
        lastConf = 0f
        lastPhase = 0f
        lastLedLevel = 0f
    }
}
