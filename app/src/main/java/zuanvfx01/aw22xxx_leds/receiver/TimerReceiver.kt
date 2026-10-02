package zuanvfx01.aw22xxx_leds.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.services.LedResolver
import zuanvfx01.aw22xxx_leds.ui.utils.TimerScheduler

/**
 * The timer alarms only wake the app up. The actual decision (are we inside the window, on an
 * enabled day, is Charger/Music more important?) is made by [LedResolver] from the clock.
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AwinicLED::TimerWakeLock")
        wakeLock.acquire(5000L)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val t = Application.INSTANCE.settings.data.first().timer
                // Re-arm first, so a hardware hiccup below can never kill the schedule.
                if (t.enabled) {
                    TimerScheduler.schedule(
                        context, t.startHour, t.startMinute, t.endHour, t.endMinute, t.daysMask
                    )
                }
                Log.d("AwinicLED", "Timer alarm ${intent.action}")
                LedResolver.apply(context, force = true)
            } catch (e: Exception) {
                Log.e("AwinicLED", "Error saat mengeksekusi Timer LED", e)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }
}
