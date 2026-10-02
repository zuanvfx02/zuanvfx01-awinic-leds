package zuanvfx01.aw22xxx_leds.services

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.AutomationConfig
import zuanvfx01.aw22xxx_leds.ChargerExtras
import zuanvfx01.aw22xxx_leds.Settings
import zuanvfx01.aw22xxx_leds.TimerSettings
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.services.LedControlGate.Owner
import zuanvfx01.aw22xxx_leds.ui.utils.TimerScheduler

/**
 * The one place that decides what the LED shows and writes it.
 *
 * Inputs are plain state: Music running?, charging + battery level?, inside the timer window?,
 * and short overlays (notification / test). Whenever any input changes, [apply] recomputes the
 * winner and writes it, so nothing is "forgotten" when a higher-priority mode ends.
 *
 * Priority (base layer):  Music  >  Charger  >  Timer  >  manual state
 * Overlay (notification): with Smart Priority ON it flashes over Charger/Timer and then the base
 *                         layer comes back. With Smart Priority OFF it only plays when nothing
 *                         from Charger/Timer is active (strict priority).
 * Music LED owns the hardware while it runs; it calls [requestApply] when it stops.
 */
object LedResolver {
    private const val TAG = "AwinicResolver"
    const val TEST_DURATION_MS = 3_000L
    const val DEFAULT_NOTIFICATION_S = 3

    val DEFAULT_LOW_COLOR: Int = 0xFFFF3B30L.toInt()
    val DEFAULT_HIGH_COLOR: Int = 0xFFFFC107L.toInt()
    val DEFAULT_FULL_COLOR: Int = 0xFF34C759L.toInt()

    /** What a "Test" button plays. */
    enum class Kind { NOTIFICATION, CHARGER, CHARGER_LOW, CHARGER_HIGH, CHARGER_FULL, TIMER }

    data class LedSpec(val effect: Int, val useOwn: Boolean, val frq: Int, val rgb: Map<Int, Int>)

    private enum class Band { LOW, NORMAL, HIGH, FULL }

    private sealed interface Base {
        object None : Base
        data class Led(val owner: Owner, val spec: LedSpec) : Base
    }

    private class Snapshot(val effect: Int, val frq: Int, val colors: List<Int>)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val overlayBusy = AtomicBoolean(false)

    @Volatile private var charging = false
    @Volatile private var batteryPercent = -1
    @Volatile private var batteryFull = false

    // Only touched while holding [mutex].
    private var holding = false
    private var snapshot: Snapshot? = null
    private var lastApplied: String? = null

    val isCharging: Boolean get() = charging

    // ---------------------------------------------------------------- public API

    /** Recompute and write. [force] = also rewrite when nothing seems to have changed. */
    fun requestApply(context: Context, force: Boolean = true) {
        val app = context.applicationContext
        scope.launch {
            runCatching { apply(app, force) }.onFailure { Log.e(TAG, "apply failed", it) }
        }
    }

    suspend fun apply(context: Context, force: Boolean = true) {
        val app = context.applicationContext
        mutex.withLock { applyLocked(app, force) }
    }

    /**
     * Reads the sticky battery state (works even when the charger was already connected before
     * the app/service started) and re-applies. [pluggedOverride] is used for the explicit
     * POWER_CONNECTED / POWER_DISCONNECTED broadcasts, which can arrive before the sticky intent updates.
     */
    fun syncBattery(context: Context, pluggedOverride: Boolean? = null, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        readBattery(app, pluggedOverride)
        scope.launch {
            try {
                apply(app, force = false)
            } catch (e: Exception) {
                Log.e(TAG, "syncBattery failed", e)
            } finally {
                onDone?.invoke()
            }
        }
    }

    /**
     * Short overlay (notification / test). Returns false when it was not played
     * (Music LED running, another overlay busy, or lower priority than an active Charger/Timer).
     */
    suspend fun playOverlay(
        context: Context,
        owner: Owner,
        spec: LedSpec,
        durationMs: Long,
        force: Boolean,
    ): Boolean {
        val app = context.applicationContext
        if (!overlayBusy.compareAndSet(false, true)) return false
        val played: Boolean = try {
            mutex.withLock {
                val prefs = Application.INSTANCE.settings.data.first()
                if (MusicLedService.isRunning(app)) return@withLock false
                if (!force && !prefs.smartOverride && computeBase(app, prefs) !is Base.None) {
                    return@withLock false
                }
                if (!holding) {
                    snapshot = capture()
                    holding = true
                }
                LedControlGate.publishAutomation(owner)
                writeSpec(spec)
                lastApplied = null
                delay(durationMs)
                applyLocked(app, force = true) // back to Charger / Timer / manual
                true
            }
        } finally {
            overlayBusy.set(false)
        }
        return played
    }

    /** The "Test" button: plays the saved config of [kind] for 3 seconds, then returns to the previous state. */
    suspend fun playTest(context: Context, kind: Kind): Boolean {
        val app = context.applicationContext
        val prefs = Application.INSTANCE.settings.data.first()
        readBattery(app, null)
        val ex = prefs.chargerExtras
        val (owner, spec) = when (kind) {
            Kind.NOTIFICATION -> Owner.NOTIFICATION to specOf(prefs.notification)
            Kind.TIMER -> Owner.TIMER to specOf(prefs.timer)
            Kind.CHARGER -> Owner.CHARGER to chargerSpec(prefs.charger, ex, bandOf(ex))
            Kind.CHARGER_LOW -> Owner.CHARGER to chargerSpec(prefs.charger, ex, Band.LOW)
            Kind.CHARGER_HIGH -> Owner.CHARGER to chargerSpec(prefs.charger, ex, Band.HIGH)
            Kind.CHARGER_FULL -> Owner.CHARGER to chargerSpec(prefs.charger, ex, Band.FULL)
        }
        return playOverlay(app, owner, spec, TEST_DURATION_MS, force = true)
    }

    // ---------------------------------------------------------------- spec helpers (shared with the notification service)

    fun specOf(c: AutomationConfig) = LedSpec(c.led.effect, c.useOwnValues, c.led.frq, c.led.rgbMap)

    fun specOf(t: TimerSettings) = LedSpec(t.led.effect, t.useOwnValues, t.led.frq, t.led.rgbMap)

    /** The same ARGB colour on every LED the hardware has. */
    fun allLeds(color: Int): Map<Int, Int> {
        val n = SysFsBridge.ledCount.takeIf { it > 0 } ?: SysFsBridge.IO.colors.size
        return (0 until n).associateWith { color }
    }

    // ---------------------------------------------------------------- internals

    private fun readBattery(context: Context, pluggedOverride: Boolean?) {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            batteryFull = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL
            charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        }
        if (pluggedOverride != null) charging = pluggedOverride
    }

    private fun isScreenOn(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    private fun bandOf(ex: ChargerExtras): Band {
        if (!ex.levelColors) return Band.NORMAL
        val low = if (ex.lowThreshold > 0) ex.lowThreshold else 20
        val high = if (ex.highThreshold > 0) ex.highThreshold else 80
        val pct = batteryPercent
        return when {
            batteryFull || pct >= 100 -> Band.FULL
            pct < 0 -> Band.NORMAL
            pct < low -> Band.LOW
            pct >= high -> Band.HIGH
            else -> Band.NORMAL
        }
    }

    private fun chargerSpec(c: AutomationConfig, ex: ChargerExtras, band: Band): LedSpec {
        val base = specOf(c)
        val color = when (band) {
            Band.LOW -> if (ex.lowColor != 0) ex.lowColor else DEFAULT_LOW_COLOR
            Band.HIGH -> if (ex.highColor != 0) ex.highColor else DEFAULT_HIGH_COLOR
            Band.FULL -> if (ex.fullColor != 0) ex.fullColor else DEFAULT_FULL_COLOR
            Band.NORMAL -> return base
        }
        return base.copy(useOwn = true, rgb = allLeds(color))
    }

    private fun computeBase(context: Context, prefs: Settings): Base {
        val c = prefs.charger
        if (c.enabled && charging) {
            val ex = prefs.chargerExtras
            if (!ex.onlyScreenOff || !isScreenOn(context)) {
                return Base.Led(Owner.CHARGER, chargerSpec(c, ex, bandOf(ex)))
            }
        }
        val t = prefs.timer
        if (t.enabled &&
            TimerScheduler.isActiveNow(t.startHour, t.startMinute, t.endHour, t.endMinute, t.daysMask)
        ) {
            return Base.Led(Owner.TIMER, specOf(t))
        }
        return Base.None
    }

    private suspend fun applyLocked(context: Context, force: Boolean) {
        val prefs = Application.INSTANCE.settings.data.first()
        if (MusicLedService.isRunning(context)) {
            // Music LED owns the hardware; it asks us to re-apply when it stops.
            lastApplied = null
            return
        }
        when (val base = computeBase(context, prefs)) {
            is Base.Led -> {
                val sig = "${base.owner}|${base.spec}"
                LedControlGate.publishAutomation(base.owner)
                if (!force && sig == lastApplied) return
                if (!holding) {
                    snapshot = capture()
                    holding = true
                }
                writeSpec(base.spec)
                lastApplied = sig
                Log.d(TAG, "base=${base.owner}")
            }
            Base.None -> {
                LedControlGate.publishAutomation(Owner.NONE)
                if (holding) {
                    restoreManual(prefs)
                    holding = false
                    snapshot = null
                    Log.d(TAG, "base=manual (restored)")
                }
                lastApplied = "NONE"
            }
        }
    }

    private fun capture() = Snapshot(
        effect = SysFsBridge.IO.currentEffect.toInt(),
        frq = SysFsBridge.IO.frequency,
        colors = SysFsBridge.IO.colors.map { it.toArgb() },
    )

    private fun writeSpec(spec: LedSpec) {
        SysFsBridge.IO.enabled = true
        SysFsBridge.IO.currentEffect = spec.effect.toUByte()
        SysFsBridge.flushCfg(false)
        if (spec.useOwn) {
            if (spec.frq != 0) SysFsBridge.IO.frequency = spec.frq
            spec.rgb.forEach { (index, colorInt) ->
                SysFsBridge.IO.setColor(index.toUByte(), Color(colorInt))
            }
            SysFsBridge.flushCfg(true)
        }
    }

    /**
     * Back to the manual state. Same gentle sequence the notification LED always used
     * (off -> restore values -> on) so the chip does not get stuck on the automation effect.
     */
    private suspend fun restoreManual(prefs: Settings) {
        val led = prefs.led
        val snap = snapshot
        var effect: Int? = null
        var frq = 0
        var colors: Map<Int, Int> = emptyMap()
        if (prefs.useSavedSettings) {
            effect = led.effect
            frq = led.frq
            colors = led.rgbMap
        } else if (snap != null) {
            effect = snap.effect
            frq = snap.frq
            colors = snap.colors.withIndex().associate { it.index to it.value }
        }

        SysFsBridge.IO.enabled = false
        SysFsBridge.flushCfg(false)
        delay(50L)

        effect?.let { SysFsBridge.IO.currentEffect = it.toUByte() }
        if (frq != 0) SysFsBridge.IO.frequency = frq
        colors.forEach { (index, colorInt) ->
            SysFsBridge.IO.setColor(index.toUByte(), Color(colorInt))
        }
        SysFsBridge.flushCfg(prefs.useOwnValues)
        delay(100L)
        SysFsBridge.IO.enabled = led.hwen
    }
}
