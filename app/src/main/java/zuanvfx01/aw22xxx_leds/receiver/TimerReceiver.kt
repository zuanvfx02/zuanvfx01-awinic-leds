package zuanvfx01.aw22xxx_leds.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.services.LedControlGate
import zuanvfx01.aw22xxx_leds.ui.utils.TimerScheduler

class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AwinicLED::TimerWakeLock")
        wakeLock.acquire(5000L)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = Application.INSTANCE.settings.data.first()
                val timerConfig = prefs.timer

                if (!timerConfig.enabled) return@launch
                if (zuanvfx01.aw22xxx_leds.services.MusicLedService.isRunning(context)) {
                    Log.d("AwinicLED", "Timer LED skipped: Music LED owns hardware")
                    return@launch
                }

                if (!LedControlGate.acquire(LedControlGate.Owner.TIMER)) {
                    Log.d("AwinicLED", "TIMER LED skipped: another LED mode owns hardware")
                    return@launch
                }

                when (action) {
                    "zuanvfx01.aw22xxx_leds.TIMER_ON" -> {
                        Log.d("AwinicLED", "Waktu Timer Tiba! Menyalakan LED...")
                        SysFsBridge.IO.enabled = true
                        SysFsBridge.IO.currentEffect = timerConfig.led.effect.toUByte()
                        SysFsBridge.flushCfg(false)

                        if (timerConfig.useOwnValues) {
                            SysFsBridge.IO.frequency = if (timerConfig.led.frq == 0) SysFsBridge.IO.frequency else timerConfig.led.frq
                            timerConfig.led.rgbMap.forEach { (index, colorInt) ->
                                SysFsBridge.IO.setColor(index.toUByte(), Color(colorInt))
                            }
                            SysFsBridge.flushCfg(true)
                        }
                    }

                    "zuanvfx01.aw22xxx_leds.TIMER_OFF" -> {
                        Log.d("AwinicLED", "Waktu Timer Habis! Mematikan LED...")
                        
                        SysFsBridge.IO.enabled = prefs.led.hwen
                        
                        SysFsBridge.IO.currentEffect = prefs.led.effect.toUByte()
                        SysFsBridge.IO.frequency = if (prefs.led.frq == 0) SysFsBridge.IO.frequency else prefs.led.frq
                        prefs.led.rgbMap.forEach { (index, colorInt) ->
                            SysFsBridge.IO.setColor(index.toUByte(), Color(colorInt))
                        }
                        SysFsBridge.flushCfg(prefs.useOwnValues)
                        
                        TimerScheduler.schedule(
                            context,
                            timerConfig.startHour, timerConfig.startMinute,
                            timerConfig.endHour, timerConfig.endMinute
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AwinicLED", "Error saat mengeksekusi Timer LED", e)
            } finally {
                LedControlGate.release(LedControlGate.Owner.TIMER)
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }
}
