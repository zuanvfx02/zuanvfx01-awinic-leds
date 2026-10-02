package zuanvfx01.aw22xxx_leds.services

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.NotificationExtras

/**
 * Plays the Notification LED. All hardware work goes through [LedResolver], which decides whether
 * the notification may interrupt Charger/Timer (Smart Priority) and restores the right state after.
 */
class LedNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastTriggerTime = 0L

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val notif = sbn.notification
        val packageName = sbn.packageName

        val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isForeground = (notif.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        val isGroupSummary = (notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0

        if (!sbn.isClearable || isOngoing || isForeground || isGroupSummary) return
        if (packageName == "android" || packageName == "com.android.systemui") return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTriggerTime < 2000L) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AwinicLED::NotifLock")
        wakeLock.acquire(20_000L)

        serviceScope.launch {
            try {
                val prefs = Application.INSTANCE.settings.data.first()
                val cfg = prefs.notification
                if (!cfg.enabled) return@launch

                val extras = prefs.notificationExtras
                if (!passesFilter(extras, packageName)) return@launch
                if (extras.onlyScreenOff && powerManager.isInteractive) return@launch
                if (extras.respectDnd && isDoNotDisturbOn()) return@launch

                // Per-app colour / effect on top of the default notification config.
                var spec = LedResolver.specOf(cfg)
                extras.appStylesMap[packageName]?.let { style ->
                    if (style.useEffect) spec = spec.copy(effect = style.effect)
                    if (style.useColor) spec = spec.copy(useOwn = true, rgb = LedResolver.allLeds(style.color))
                }

                val seconds = if (extras.durationS > 0) extras.durationS else LedResolver.DEFAULT_NOTIFICATION_S
                lastTriggerTime = currentTime
                val played = LedResolver.playOverlay(
                    this@LedNotificationService,
                    LedControlGate.Owner.NOTIFICATION,
                    spec,
                    seconds * 1000L,
                    force = false,
                )
                if (!played) Log.d("AwinicLED", "Notification LED skipped (Music LED / higher priority / busy)")
            } catch (e: Exception) {
                Log.e("AwinicLED", "Error hardware saat notifikasi", e)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    private fun passesFilter(extras: NotificationExtras, packageName: String): Boolean = when (extras.filterMode) {
        1 -> extras.packagesList.contains(packageName)
        2 -> !extras.packagesList.contains(packageName)
        else -> true
    }

    private fun isDoNotDisturbOn(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val filter = nm.currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }
}
