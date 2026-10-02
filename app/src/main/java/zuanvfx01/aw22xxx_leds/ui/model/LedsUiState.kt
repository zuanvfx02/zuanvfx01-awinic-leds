package zuanvfx01.aw22xxx_leds.ui.model

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.bridge.LedEffect
import zuanvfx01.aw22xxx_leds.bridge.LedIMax
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.services.LedResolver
import zuanvfx01.aw22xxx_leds.ui.utils.TimerScheduler

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
    val colors: List<Color>,
    val daysMask: Int = TimerScheduler.ALL_DAYS, // already resolved: 0 (legacy) -> all days
)

data class AppStyleUi(val useColor: Boolean, val color: Int, val useEffect: Boolean, val effect: Int)

data class NotificationExtrasUi(
    val durationS: Int,
    val onlyScreenOff: Boolean,
    val respectDnd: Boolean,
    val filterMode: Int, // 0 all, 1 only listed, 2 all except listed
    val packages: List<String>,
    val appStyles: Map<String, AppStyleUi>,
)

data class ChargerExtrasUi(
    val levelColors: Boolean,
    val lowThreshold: Int,
    val highThreshold: Int,
    val lowColor: Int,
    val highColor: Int,
    val fullColor: Int,
    val onlyScreenOff: Boolean,
)

// Ringkasan konfigurasi Music LED untuk Home (default sama dengan layar Music LED).
data class MusicUiState(
    val enabled: Boolean,
    val sensitivity: Int,
    val minFrequency: Int,
    val maxFrequency: Int,
    val randomEffects: Boolean,
    val dynamicColors: Boolean
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
    val music: MusicUiState,

    val smartOverride: Boolean,
    val pollingSaver: Boolean = false,
    val notificationExtras: NotificationExtrasUi = NotificationExtrasUi(3, false, false, 0, emptyList(), emptyMap()),
    val chargerExtras: ChargerExtrasUi = ChargerExtrasUi(false, 20, 80, 0, 0, 0, false),
) {
    companion object {
        /** One-shot synchronous load, used ONLY to seed the ViewModel's initial state. */
        fun loadBlocking(context: Context): LedsUiState = runBlocking { load(context) }

        suspend fun load(context: Context): LedsUiState {
            val protoData = Application.INSTANCE.settings.data.first()
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
                    colors = parsedColors,
                    daysMask = TimerScheduler.effectiveMask(config.daysMask)
                )
            }

            fun parseNotificationExtras(e: zuanvfx01.aw22xxx_leds.NotificationExtras) = NotificationExtrasUi(
                durationS = if (e.durationS > 0) e.durationS.coerceIn(1, 15) else LedResolver.DEFAULT_NOTIFICATION_S,
                onlyScreenOff = e.onlyScreenOff,
                respectDnd = e.respectDnd,
                filterMode = e.filterMode.coerceIn(0, 2),
                packages = e.packagesList.toList(),
                appStyles = e.appStylesMap.mapValues { (_, v) ->
                    AppStyleUi(v.useColor, v.color, v.useEffect, v.effect)
                }
            )

            fun parseChargerExtras(e: zuanvfx01.aw22xxx_leds.ChargerExtras) = ChargerExtrasUi(
                levelColors = e.levelColors,
                lowThreshold = if (e.lowThreshold > 0) e.lowThreshold else 20,
                highThreshold = if (e.highThreshold > 0) e.highThreshold else 80,
                lowColor = if (e.lowColor != 0) e.lowColor else LedResolver.DEFAULT_LOW_COLOR,
                highColor = if (e.highColor != 0) e.highColor else LedResolver.DEFAULT_HIGH_COLOR,
                fullColor = if (e.fullColor != 0) e.fullColor else LedResolver.DEFAULT_FULL_COLOR,
                onlyScreenOff = e.onlyScreenOff
            )

            fun parseMusic(config: zuanvfx01.aw22xxx_leds.MusicLedConfig): MusicUiState {
                val minF = if (config.minFrequency == 0) 10 else config.minFrequency.coerceIn(1, 100)
                val maxF = if (config.maxFrequency == 0) 80 else config.maxFrequency.coerceIn(1, 100)
                return MusicUiState(
                    enabled = config.enabled,
                    sensitivity = if (config.sensitivity == 0) 5 else config.sensitivity.coerceIn(1, 10),
                    minFrequency = minF,
                    maxFrequency = maxOf(minF, maxF),
                    randomEffects = config.useRandomEffects,
                    dynamicColors = config.useDynamicColors
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
                music = parseMusic(protoData.music),
                
                smartOverride = protoData.smartOverride,
                pollingSaver = protoData.pollingSaver,
                notificationExtras = parseNotificationExtras(protoData.notificationExtras),
                chargerExtras = parseChargerExtras(protoData.chargerExtras)
            )
        }
    }
}
