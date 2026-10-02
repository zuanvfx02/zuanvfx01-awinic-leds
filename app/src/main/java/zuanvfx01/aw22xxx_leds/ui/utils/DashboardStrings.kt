package zuanvfx01.aw22xxx_leds.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Default (English) texts of the Music LED dashboard. They are merged into the language-pack
 * defaults, so every key can be translated/renamed from an imported language pack. The six
 * `music_led_name_N` keys are the LED names shown in the dashboard stack (1 = bottom).
 */
object DashboardStrings {
    val defaults: Map<String, String> = linkedMapOf(
        "dash_state_idle" to "Idle",
        "dash_state_waiting" to "Waiting for sound",
        "dash_state_detecting" to "Detecting",
        "dash_state_beat" to "Beat",
        "dash_bpm" to "BPM",
        "dash_confidence" to "Confidence",
        "dash_beats" to "Beats",
        "dash_stage_mic" to "Mic",
        "dash_stage_flux" to "Flux",
        "dash_stage_threshold" to "Threshold",
        "dash_stage_led" to "LED",
        "dash_idle_hint" to "Music LED is off. Enable it to see live data here.",
        "dash_level_title" to "Sound level",
        "dash_level_hint" to "Bars show microphone loudness over the last ~3 seconds. Below the dashed gate line the LED ignores sound.",
        "dash_legend_sound" to "Sound",
        "dash_legend_gate" to "Gate",
        "dash_legend_beat" to "Beat",
        "dash_gate_above" to "Above gate",
        "dash_gate_below" to "Below gate",
        "dash_detect_title" to "How a beat is detected",
        "dash_detect_hint" to "Onsets are candidate beats. The tracker combines spectral rise, adaptive threshold, tempo phase, and confidence before the LED fires. Higher sensitivity requires stronger evidence.",
        "dash_detect_hint_aggressive" to "The LED changes when onset strength (how fast the spectrum is rising) jumps above the adaptive threshold. The sensitivity slider moves that threshold.",
        "dash_legend_flux" to "Onset strength",
        "dash_legend_threshold" to "Threshold",
        "dash_legend_trigger" to "LED trigger",
        "dash_ratio_label" to "Now",
        "dash_led_title" to "LED response",
        "dash_led_hint" to "LEDs light from the bottom up with loudness and beat strength, then fade out.",
        "music_led_name_1" to "LED 1 · Base",
        "music_led_name_2" to "LED 2 · Low",
        "music_led_name_3" to "LED 3 · Mid",
        "music_led_name_4" to "LED 4 · High",
        "music_led_name_5" to "LED 5 · Peak",
        "music_led_name_6" to "LED 6 · Max",
        "dash_events_title" to "Recent LED changes",
        "dash_events_empty" to "No LED changes yet",
        "dash_src_onset" to "Onset",
        "dash_src_predicted" to "Predicted",
        "dash_src_fallback" to "Fallback",
        "dash_event_effect" to "Effect",
        "dash_event_now" to "now",
        "home_overview_title" to "Automation overview",
        "home_overview_active" to "%1\$d of %2\$d active",
        "home_owner_label" to "LED controlled by",
        "home_owner_idle" to "Manual / idle",
        "home_owner_music" to "Music LED",
        "home_owner_notification" to "Notification LED",
        "home_owner_charger" to "Charger LED",
        "home_owner_timer" to "Timer Schedule",
        "home_state_enabled" to "Enabled",
        "home_state_disabled" to "Disabled",
        "home_state_running" to "Running",
        "home_state_needs_access" to "Needs access",
        "home_smart_on" to "Notifications flash over Charger / Timer, then they return",
        "home_smart_off" to "Strict order: Music > Charger > Timer > Notification",
        "home_notification_access_needed" to "Notification access not granted",
        "home_notification_detail" to "%1\$s · %2\$d Hz",
        "home_music_running" to "Listening to the microphone",
        "home_music_ready" to "Sensitivity %1\$d/10 · %2\$d–%3\$d Hz",
        "home_timer_detail" to "%1\$s – %2\$s",
        "home_charger_detail" to "%1\$s · %2\$d Hz",
        "home_music_bpm" to "%1\$d BPM",
    )
}

/** Resolves every dashboard text once; call it from a composable that does NOT recompose per frame. */
class DashTexts(private val map: Map<String, String>) {
    operator fun get(key: String): String = map[key] ?: DashboardStrings.defaults[key] ?: key
}

@Composable
fun rememberDashTexts(): DashTexts {
    val context = LocalContext.current
    val version = LanguagePackManager.version.value // re-resolve when a language pack changes
    return remember(context, version) {
        DashTexts(
            DashboardStrings.defaults.mapValues { (key, fallback) ->
                LanguagePackManager.get(context, key, fallback)
            }
        )
    }
}
