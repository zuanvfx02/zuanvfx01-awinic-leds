package zuanvfx01.aw22xxx_leds.services

import android.app.Notification
import android.content.Context
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge

class LedNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var isPlayingNotification = false
    private var lastTriggerTime = 0L

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

        if (isPlayingNotification) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTriggerTime < 2000L) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AwinicLED::NotifLock")
        wakeLock.acquire(5000L)

        serviceScope.launch {
            try {
                val prefs = Application.INSTANCE.settings.data.first()
                val notifConfig = prefs.notification

                if (!notifConfig.enabled) return@launch
                if (MusicLedService.isRunning(this@LedNotificationService)) {
                    Log.d("AwinicLED", "Notification LED skipped: Music LED owns hardware")
                    return@launch
                }

                if (!LedControlGate.acquire(LedControlGate.Owner.NOTIFICATION)) return@launch
                isPlayingNotification = true
                lastTriggerTime = currentTime

                val originalEnabled = SysFsBridge.IO.enabled
                val originalEffect = SysFsBridge.IO.currentEffect
                val originalFreq = SysFsBridge.IO.frequency
                val originalColors = SysFsBridge.IO.colors.toList()

                SysFsBridge.IO.enabled = true
                SysFsBridge.IO.currentEffect = notifConfig.led.effect.toUByte()
                SysFsBridge.flushCfg(false) 

                if (notifConfig.useOwnValues) {
                    SysFsBridge.IO.frequency = if (notifConfig.led.frq == 0) originalFreq else notifConfig.led.frq
                    notifConfig.led.rgbMap.forEach { (index, colorInt) ->
                        SysFsBridge.IO.setColor(index.toUByte(), Color(colorInt))
                    }
                    SysFsBridge.flushCfg(true)
                }

                delay(3000L)

                SysFsBridge.IO.enabled = false
                SysFsBridge.flushCfg(false)
                delay(50L)

                SysFsBridge.IO.currentEffect = originalEffect
                SysFsBridge.IO.frequency = originalFreq
                originalColors.forEachIndexed { index, color ->
                    SysFsBridge.IO.setColor(index.toUByte(), color)
                }
                
                SysFsBridge.flushCfg(prefs.useOwnValues)
                delay(100L)
                
SysFsBridge.IO.enabled = originalEnabled
            } catch (e: Exception) {
                Log.e("AwinicLED", "Error hardware saat notifikasi", e)
            } finally {
                isPlayingNotification = false
                LedControlGate.release(LedControlGate.Owner.NOTIFICATION)
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }
}
