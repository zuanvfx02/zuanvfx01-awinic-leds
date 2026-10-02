package zuanvfx01.aw22xxx_leds.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.SavedLedSettings
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.services.ChargerService
import zuanvfx01.aw22xxx_leds.services.LedResolver
import zuanvfx01.aw22xxx_leds.ui.utils.TimerScheduler

/**
 * Re-applies the saved LED settings after boot.
 *
 * The Magisk module (service.sh) only relabels/chmods the sysfs nodes AFTER sys.boot_completed,
 * so on some ROMs this receiver runs first and its writes would silently fail. Instead of
 * writing once and hoping, we retry every [RETRY_INTERVAL_MS] until the nodes are writable,
 * for at most [MAX_WAIT_MS]. goAsync() keeps the broadcast alive while we wait, and nothing
 * here blocks a thread with runBlocking.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context?.applicationContext ?: return
        val isBoot = intent?.action == Intent.ACTION_BOOT_COMPLETED
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Only a real boot needs the saved manual state written again.
                if (isBoot) applySavedSettings()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply saved LED settings", e)
            }
            try {
                restoreAutomations(ctx)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore automations", e)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Alarms do not survive a reboot / app update / clock change, and nothing restarted the
     * charger monitor after boot. Bring both back, then let the resolver pick the right LED state
     * (e.g. charger already plugged in, or we are inside the timer window).
     */
    private suspend fun restoreAutomations(ctx: Context) {
        val prefs = Application.INSTANCE.settings.data.first()
        val t = prefs.timer
        if (t.enabled) {
            TimerScheduler.schedule(ctx, t.startHour, t.startMinute, t.endHour, t.endMinute, t.daysMask)
        }
        if (prefs.charger.enabled) {
            // Starting a foreground service can be refused when the app is replaced in the background.
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, ChargerService::class.java)) }
                .onFailure { Log.w(TAG, "ChargerService not started", it) }
        }
        LedResolver.syncBattery(ctx)
    }

    private suspend fun applySavedSettings() {
        val settings = Application.INSTANCE.settings.data.first()
        if (!settings.useSavedSettings) return

        val saved = settings.led
        val useOwnValues = settings.useOwnValues

        val start = SystemClock.elapsedRealtime()
        var attempt = 0
        while (true) {
            attempt++
            if (SysFsBridge.isWriteReady()) {
                apply(saved, useOwnValues)
                Log.i(TAG, "Saved LED settings applied (attempt $attempt, +${SystemClock.elapsedRealtime() - start}ms)")
                return
            }
            if (SystemClock.elapsedRealtime() - start >= MAX_WAIT_MS) break
            delay(RETRY_INTERVAL_MS)
        }

        // Nodes never looked writable. Try one last best-effort write and say so in logcat.
        Log.w(TAG, "LED nodes not writable after ${MAX_WAIT_MS}ms ($attempt attempts); trying once anyway")
        apply(saved, useOwnValues)
    }

    private fun apply(saved: SavedLedSettings, useOwnValues: Boolean) {
        SysFsBridge.IO.enabled = saved.hwen
        SysFsBridge.IO.currentEffect = saved.effect.toUByte()
        SysFsBridge.IO.frequency = saved.frq

        for (entry in saved.rgbMap) {
            SysFsBridge.IO.setColor(entry.key.toUByte(), Color(entry.value))
        }

        SysFsBridge.flushCfg(useOwnValues)
    }

    private companion object {
        const val TAG = "AwinicBoot"
        const val RETRY_INTERVAL_MS = 2_000L
        const val MAX_WAIT_MS = 30_000L
    }
}
