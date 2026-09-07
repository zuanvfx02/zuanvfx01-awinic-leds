package zuanvfx01.aw22xxx_leds.ui.model

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.bridge.LedEffect
import zuanvfx01.aw22xxx_leds.bridge.LedIMax
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge

data class AutomationUiState(
    val enabled: Boolean,
    val useOwnValues: Boolean,
    val effect: Int,
    val frequency: Int,
    val colors: List<Color>
)

// Menambahkan TimerUiState untuk menampung variabel jam
data class TimerUiState(
    val enabled: Boolean,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val useOwnValues: Boolean,
    val effect: Int,
    val frequency: Int,
    val colors: List<Color>
)

data class LedsUiState(
    val enabled: Boolean,
    val currentIMax: LedIMax.Values,
    val currentEffect: Int,
    val availableEffect: List<LedEffect>,
    val frequency: Int,
    val colors: List<Color>,
    val useOwnValues: Boolean,
    val useSavedSettings: Boolean,
    
    val notification: AutomationUiState,
    val charger: AutomationUiState,
    val timer: TimerUiState, // Ubah tipe jadi TimerUiState

    val smartOverride: Boolean
) {
    companion object {
        fun load(context: Context): LedsUiState {
            val protoData = runBlocking { Application.INSTANCE.settings.data.first() }
            val defaultColors = SysFsBridge.IO.colors

            fun parseAutomation(config: zuanvfx01.aw22xxx_leds.AutomationConfig): AutomationUiState {
                val protoColors = config.led.rgbMap
                val parsedColors = defaultColors.mapIndexed { index, defaultColor ->
                    protoColors[index]?.let { Color(it) } ?: defaultColor
                }
                return AutomationUiState(
                    enabled = config.enabled,
                    useOwnValues = config.useOwnValues,
                    effect = config.led.effect,
                    frequency = if (config.led.frq == 0) SysFsBridge.IO.frequency else config.led.frq,
                    colors = parsedColors
                )
            }

            // Fungsi parse khusus untuk Timer
            fun parseTimer(config: zuanvfx01.aw22xxx_leds.TimerSettings): TimerUiState {
                val protoColors = config.led.rgbMap
                val parsedColors = defaultColors.mapIndexed { index, defaultColor ->
                    protoColors[index]?.let { Color(it) } ?: defaultColor
                }
                return TimerUiState(
                    enabled = config.enabled,
                    startHour = config.startHour,
                    startMinute = config.startMinute,
                    endHour = config.endHour,
                    endMinute = config.endMinute,
                    useOwnValues = config.useOwnValues,
                    effect = config.led.effect,
                    frequency = if (config.led.frq == 0) SysFsBridge.IO.frequency else config.led.frq,
                    colors = parsedColors
                )
            }

            return LedsUiState(
                enabled = protoData.led.hwen,
                currentIMax = SysFsBridge.IO.currentIMax,
                currentEffect = SysFsBridge.IO.currentEffect.toInt(),
                availableEffect = SysFsBridge.IO.availableEffects,
                frequency = SysFsBridge.IO.frequency,
                colors = defaultColors,
                useOwnValues = protoData.useOwnValues,
                useSavedSettings = protoData.useSavedSettings,
                
                notification = parseAutomation(protoData.notification),
                charger = parseAutomation(protoData.charger),
                timer = parseTimer(protoData.timer), // Gunakan parseTimer
                
                smartOverride = protoData.smartOverride
            )
        }
    }
}
