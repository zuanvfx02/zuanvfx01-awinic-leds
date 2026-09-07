package zuanvfx01.aw22xxx_leds.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.services.LedControlGate

class ChargerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = Application.INSTANCE.settings.data.first()
                val chargerConfig = prefs.charger

                if (!chargerConfig.enabled) return@launch
                if (zuanvfx01.aw22xxx_leds.services.MusicLedService.isRunning(context)) {
                    Log.d("AwinicLED", "Charger LED skipped: Music LED owns hardware")
                    return@launch
                }

                if (!LedControlGate.acquire(LedControlGate.Owner.CHARGER)) {
                    Log.d("AwinicLED", "CHARGER LED skipped: another LED mode owns hardware")
                    return@launch
                }

                when (action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        Log.d("AwinicLED", "Charger Dicolok! Menyalakan LED...")
                        SysFsBridge.IO.enabled = true
                        SysFsBridge.IO.currentEffect = chargerConfig.led.effect.toUByte()
                        SysFsBridge.flushCfg(false)

                        if (chargerConfig.useOwnValues) {
                            SysFsBridge.IO.frequency = if (chargerConfig.led.frq == 0) SysFsBridge.IO.frequency else chargerConfig.led.frq
                            chargerConfig.led.rgbMap.forEach { (index, colorInt) ->
                                SysFsBridge.IO.setColor(index.toUByte(), Color(colorInt))
                            }
                            SysFsBridge.flushCfg(true)
                        }
                    }
                    
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        Log.d("AwinicLED", "Charger Dicabut! Mengembalikan pengaturan...")
                        
                        SysFsBridge.IO.enabled = prefs.led.hwen
                        
                        SysFsBridge.IO.currentEffect = prefs.led.effect.toUByte()
                        SysFsBridge.IO.frequency = if (prefs.led.frq == 0) SysFsBridge.IO.frequency else prefs.led.frq
                        prefs.led.rgbMap.forEach { (index, colorInt) ->
                            SysFsBridge.IO.setColor(index.toUByte(), Color(colorInt))
                        }
                        SysFsBridge.flushCfg(prefs.useOwnValues)
                    }
                }
            } catch (e: Exception) {
                Log.e("AwinicLED", "Error saat memproses Charger LED", e)
            } finally {
                LedControlGate.release(LedControlGate.Owner.CHARGER)
                pendingResult.finish()
            }
        }
    }
}
