package zuanvfx01.aw22xxx_leds.ui.utils

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * User-editable language packs. The app ships in English; a language pack is a
 * portable JSON dictionary that can be edited in any text editor and imported
 * without changing the APK.
 */
object LanguagePackManager {
    private const val PREFS = "language_pack"
    private const val KEY_OVERRIDES = "overrides"
    private const val FORMAT = 1
    private val versionState = mutableIntStateOf(0)
    val version: MutableState<Int> get() = versionState

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, key: String, fallback: String): String {
        val raw = prefs(context).getString(KEY_OVERRIDES, null) ?: return fallback
        return runCatching { JSONObject(raw).optString(key, fallback) }.getOrDefault(fallback)
    }

    fun setOverrides(context: Context, values: Map<String, String>) {
        prefs(context).edit().putString(KEY_OVERRIDES, JSONObject(values).toString()).apply()
        versionState.intValue++
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_OVERRIDES).apply()
        versionState.intValue++
    }

    fun export(context: Context, uri: Uri) {
        val json = JSONObject().apply {
            put("format", "awinic-leds-language")
            put("format_version", FORMAT)
            put("app", "Awinic Leds")
            put("base_language", "en")
            put("language_name", "Custom")
            put("instructions", "Edit the values in 'strings', keep the keys unchanged, then import this file into Awinic Leds.")
            val strings = JSONObject()
            defaults.forEach { (key, fallback) -> strings.put(key, get(context, key, fallback)) }
            put("strings", strings)
        }
        context.contentResolver.openOutputStream(uri)?.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { it.write(json.toString(2)) }
        } ?: error("Unable to open export destination")
    }

    fun import(context: Context, uri: Uri): Int {
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { it.readText() }
        } ?: error("Unable to open language pack")
        val root = JSONObject(json)
        require(root.optString("format") == "awinic-leds-language") { "Not an Awinic Leds language pack" }
        require(root.optInt("format_version", -1) == FORMAT) { "Unsupported language pack version" }
        val strings = root.optJSONObject("strings") ?: error("Missing strings object")
        val accepted = linkedMapOf<String, String>()
        defaults.keys.forEach { key ->
            if (strings.has(key)) {
                accepted[key] = strings.optString(key, defaults[key].orEmpty())
            }
        }
        require(accepted.isNotEmpty()) { "No known strings found" }
        setOverrides(context, accepted)
        return accepted.size
    }

    val defaults: Map<String, String> = linkedMapOf(
        "app_name" to "Awinic Leds",
        "update_available_title" to "Update available",
        "update_available_message" to "A newer module version %s is available. Your version is %s. Download the module ZIP and flash it manually in Magisk.",
        "update_downloading" to "Downloading module %s...",
        "update_downloading_button" to "Downloading...",
        "update_now" to "Download module",
        "update_later" to "Later",
        "welcome_title" to "Hello!",
        "welcome_text" to "This app is designed to restore the functionality of the Awinic (aw22xxx) LEDs on the POCO F4 GT.\n\nThe app may work on other devices that have these LEDs, but it is not guaranteed.\nPlease take this into account.\n\nIf you think the app should work on your firmware or device, but the app does not work, create an issue on the project's GitHub page.",
        "continue_button" to "Continue",
        "led_hwen_title" to "Enabled",
        "led_effect_title" to "Effect",
        "led_use_own_values_title" to "Use own values",
        "led_use_own_values_description" to "Replace default colors and freq by own",
        "ok" to "OK",
        "led_frq_title" to "Frequency",
        "cat_own_values" to "Own values",
        "led_rgb_title" to "Color #%1\$d",
        "cat_application" to "Application",
        "led_use_saved_settings_title" to "Use saved settings",
        "led_use_saved_settings_description" to "Apply saved settings after reboot",
        "access_exception_title" to "Failed to access sysfs files!",
        "back" to "Back",
        "nav_home" to "Home",
        "nav_settings" to "Settings",
        "nav_info" to "Info",
        "home_subtitle" to "Controller Overview",
        "value_none" to "None",
        "home_section_controller" to "Controller",
        "home_status" to "Status",
        "home_status_online" to "Online",
        "home_status_offline" to "Offline",
        "home_sysfs_path" to "SysFs Path",
        "home_imax" to "IMax",
        "value_unknown" to "Unknown",
        "home_trigger" to "Trigger",
        "home_section_state" to "State",
        "home_leds_on" to "On",
        "home_leds_off" to "Off",
        "home_effect" to "Effect",
        "home_frequency" to "Frequency",
        "value_hz" to "%1\$s Hz",
        "cancel" to "Cancel",
        "scroll_to_select" to "Scroll to select",
        "app_section" to "App",
        "version_format" to "Version %s",
        "diagnostics_section" to "Diagnostics",
        "export_led_log" to "Export LED sysfs log",
        "export_led_log_description" to "Read all diagnostic nodes and create a clean log file",
        "log_created" to "Log created: %s",
        "log_create_failed" to "Failed to create log: %s",
        "share" to "Share",
        "led_sysfs_path" to "LED sysfs path",
        "about_section" to "About",
        "source_code" to "Source Code",
        "source_code_description" to "View project on GitHub",
        "compatible_devices" to "Compatible Devices",
        "compatible_devices_description" to "POCO F4 GT, Black Shark (aw22xxx driver)",
        "license" to "License",
        "license_description" to "View Open Source Licenses",
        "developers" to "Developers & Contributors",
        "lead_developer" to "Lead Developer",
        "led_section" to "LED",
        "led_manual_supporting" to "Turn the LED on manually. Automation does not change this switch.",
        "automation_section" to "Automation",
        "smart_priority" to "Smart Priority Mode",
        "notification_led" to "Notification LED",
        "charger_led" to "Charger LED",
        "timer_schedule" to "Timer Schedule",
        "music_led" to "Music LED",
        "music_led_description" to "LED follows the rhythm of music",
        "requires_notification_access" to "Requires notification access",
        "enabled" to "Enabled",
        "disabled" to "Disabled",
        "master_switch" to "MASTER SWITCH",
        "configuration" to "CONFIGURATION",
        "led_values" to "LED VALUES",
        "schedule_time" to "SCHEDULE TIME",
        "effect_during_schedule" to "Effect played during the schedule",
        "effect_while_charging" to "Effect played while charging",
        "customize_color_frequency" to "Customize color and frequency",
        "checking_device" to "Checking device",
        "permission_restricted_note" to "Permission denied is recorded as restricted, not as a missing driver.",
        "compatibility_report" to "AWINIC LED COMPATIBILITY REPORT",
        "driver_evidence" to "Driver evidence",
        "device" to "Device",
        "result" to "RESULT",
        "scan_again" to "Scan again",
        "open_menu" to "Open menu",
        "compatibility_result" to "Compatibility result",
        "driver_not_found" to "LED driver not found",
        "compatibility_failure_description" to "The check was performed once after the intro. The checker does not use root or libsu.",
        "splash_subtitle" to "LED controller",
        "walkthrough_skip" to "Skip",
        "walkthrough_continue" to "Continue",
        "walkthrough_get_started" to "Get started",
        "tutorial_show_me" to "Show me",
        "tutorial_done" to "Done",
        "music_active" to "Music LED Active",
        "music_ready" to "Music LED Ready",
        "music_listening" to "Listening to microphone input",
        "music_ready_description" to "Enable to react to sound and beats",
        "high_battery_usage" to "High battery usage",
        "high_battery_description" to "Music LED can use noticeably more battery than normal LED control because the microphone, audio analysis and foreground service stay active while it runs.",
        "microphone_permission_needed" to "Microphone permission needed",
        "microphone_permission_description" to "Android requires microphone access for this test mode.",
        "allow_microphone" to "Allow microphone",
        "audio_capture_not_running" to "Audio capture is not running",
        "start_service_description" to "Start the foreground service to begin beat detection.",
        "start_detection" to "Start detection",
        "effect_section" to "EFFECT",
        "color_section" to "COLOR",
        "how_it_works" to "HOW IT WORKS",
        "beat_sensitivity" to "Beat sensitivity",
        "beat_sensitivity_description" to "Lower = reacts easier · Higher = stronger beats only",
        "led_frequency_range" to "LED frequency range",
        "led_frequency_description" to "Controls the effect frequency, not the microphone audio range.",
        "enabled_effects" to "Enabled effects",
        "language_section" to "Language",
        "export_language_pack" to "Export language pack",
        "export_language_pack_description" to "Create an editable JSON file for translating the app",
        "import_language_pack" to "Import language pack",
        "import_language_pack_description" to "Apply a translated JSON language pack to this app",
        "language_exported" to "Language pack exported",
        "language_imported" to "Language pack imported: %d strings",
        "language_import_failed" to "Language pack import failed: %s",
        "language_export_failed" to "Language pack export failed: %s",
        "quick_tour" to "Quick tour",
        "quick_tour_description" to "Let's take a few seconds to show you the most useful LED controls.",
        "open_settings" to "Open Settings",
        "open_settings_description" to "This is where you control LED effects, automation and Music LED.",
        "tap_settings" to "Tap Settings",
        "open_music_led" to "Open Music LED",
        "open_music_led_description" to "Music LED reacts to sound and beats. Open it to see the live controls.",
        "tap_music_led" to "Tap Music LED",
        "enable_music_led" to "Enable Music LED",
        "enable_music_led_description" to "Turn this switch on to start the feature. Android may ask for microphone permission.",
        "tap_enable_music_led" to "Tap Enable Music LED",
        "intro_eyebrow_controller" to "AWINIC LED CONTROLLER",
        "intro_title_alive" to "Make your LEDs feel alive.",
        "intro_desc_alive" to "Control effects, colors, frequency and brightness from one clean Fluent-style interface.",
        "intro_eyebrow_precision" to "PRECISION CONTROL",
        "intro_title_precision" to "Tune every detail.",
        "intro_desc_precision" to "Choose your effect, set your own RGB values and keep your preferred settings after reboot.",
        "intro_eyebrow_automation" to "SMART AUTOMATION",
        "intro_title_automation" to "Let the LEDs react for you.",
        "intro_desc_automation" to "Notification, charger, timer and music modes can take over automatically when you need them.",
        "intro_eyebrow_ready" to "READY TO START",
        "intro_title_ready" to "Your setup. Your light.",
        "intro_desc_ready" to "The app will check your device for a compatible Awinic LED controller before opening the dashboard.",
        "music_battery_description" to "Music LED can use noticeably more battery than normal LED control. It keeps the microphone active, runs continuous audio analysis in a foreground service, and may write LED settings repeatedly while music is playing.",
        "music_battery_tip" to "Tip: turn it off when you are finished. The notification above stays available while the service is running so you can stop it quickly.",
        "music_section" to "MUSIC LED",
        "music_pipeline" to "Microphone → detector → LED",
        "music_pipeline_description" to "Audio energy is measured continuously, beats trigger effects, and silence fades the LED back out.",
        "foreground_service" to "Foreground service",
        "foreground_service_description" to "The detector can keep running while you browse other screens.",
        "live_microphone" to "Live microphone beat detection",
        "minimum_frequency" to "Minimum · %d Hz",
        "maximum_frequency" to "Maximum · %d Hz",
        "manual_led_description" to "Turn the LED on manually. Automation does not change this switch.",
        "smart_priority_description" to "Automation may temporarily take control of the LED while preserving the Manual switch state.",
        "music_follow_description" to "LED follows the rhythm of music",
        "tutorial_three_steps" to "3 simple steps",
        "music_react_description" to "React to sound captured by the device microphone",
        "microphone_granted" to "Granted · ready to capture audio",
        "microphone_required" to "Required before Music LED can start",
        "random_effects" to "Random effects",
        "random_effects_description" to "Cycle through the selected effects on every beat",
        "dynamic_colors" to "Dynamic colors",
        "dynamic_colors_description" to "Shift the LED palette as beats are detected",
        "own_colors_description" to "Use your saved LED colors instead of generated colors",
        "enable_charger_led" to "Enable Charger LED",
        "charger_led_description" to "Turn on LED when device is plugged in",
        "enable_notification_led" to "Enable Notification LED",
        "notification_led_description" to "Turn on LED when a new notification arrives",
        "notification_effect_description" to "Effect played when notification arrives",
        "enable_timer" to "Enable Timer",
        "timer_description" to "Automatically control the LED at specific times",
        "start_time" to "Start Time (Turn ON)",
        "end_time" to "End Time (Turn OFF)"
    )
}
