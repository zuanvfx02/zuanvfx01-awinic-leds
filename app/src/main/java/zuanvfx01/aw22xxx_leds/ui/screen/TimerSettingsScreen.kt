package zuanvfx01.aw22xxx_leds.ui.screen

import zuanvfx01.aw22xxx_leds.ui.utils.appText
import zuanvfx01.aw22xxx_leds.ui.utils.appString
import android.app.TimePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.services.LedResolver
import zuanvfx01.aw22xxx_leds.ui.utils.TimerScheduler
import zuanvfx01.aw22xxx_leds.ui.glass.GlassTimePickerDialog
import zuanvfx01.aw22xxx_leds.ui.glass.rememberGlassDialogsAvailable
import zuanvfx01.aw22xxx_leds.ui.model.LedsViewModel
import zuanvfx01.aw22xxx_leds.ui.widgets.ChoiceRow
import zuanvfx01.aw22xxx_leds.ui.widgets.DaysOfWeekRow
import zuanvfx01.aw22xxx_leds.ui.widgets.TestLedRow
import zuanvfx01.aw22xxx_leds.ui.widgets.pickerLabel
import zuanvfx01.aw22xxx_leds.ui.widgets.rememberLedTester
import zuanvfx01.aw22xxx_leds.ui.widgets.ColorRow
import zuanvfx01.aw22xxx_leds.ui.widgets.ScrollFieldRow
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsGroup
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsRow
import zuanvfx01.aw22xxx_leds.ui.widgets.SwitchRow
import zuanvfx01.aw22xxx_leds.ui.widgets.TabScaffold

@Composable
fun TimerSettingsScreen(viewModel: LedsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val frequencies = remember { (1..100).toList() }
    val cfg = uiState.timer
    fun time(h: Int, m: Int) = "%02d:%02d".format(h, m)

    // Android 12+: Compose glass time picker. Android 11-: the platform TimePickerDialog, as before.
    val glassDialogs = rememberGlassDialogsAvailable()
    var picker by remember { mutableStateOf<TimerPicker?>(null) }
    val test = rememberLedTester(viewModel)

    // "Starts in 2h 10m" / "Active now · ends in 3h": refreshed every 30 s while the screen is open.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val startsInText = appText("timer_starts_in", "Starts in %s")
    val activeText = appText("timer_active_ends_in", "Active now · ends in %s")
    val sameTimeText = appText("timer_same_time", "Start and end time are the same, nothing will happen")
    val status = remember(cfg.startHour, cfg.startMinute, cfg.endHour, cfg.endMinute, cfg.daysMask, nowMs) {
        if (cfg.startHour == cfg.endHour && cfg.startMinute == cfg.endMinute) {
            sameTimeText
        } else if (TimerScheduler.isActiveNow(cfg.startHour, cfg.startMinute, cfg.endHour, cfg.endMinute, cfg.daysMask)) {
            String.format(activeText, TimerScheduler.formatDuration(TimerScheduler.nextEndMillis(cfg.endHour, cfg.endMinute, nowMs) - nowMs))
        } else {
            String.format(startsInText, TimerScheduler.formatDuration(TimerScheduler.nextStartMillis(cfg.startHour, cfg.startMinute, cfg.daysMask, nowMs) - nowMs))
        }
    }

    TabScaffold(appText("timer_schedule", "Timer Schedule"), appText("automation_section", "Automation"), onBack = onBack) {
        item {
            SettingsGroup(title = appText("master_switch", "MASTER SWITCH")) {
                item { SwitchRow(cfg.enabled, { viewModel.setEnableTimerLed(it) }, appText("enable_timer", "Enable Timer"), appText("timer_description", "Automatically control the LED at specific times")) }
                if (cfg.enabled) {
                    item { SettingsRow(appText("timer_status", "Status"), supporting = status) }
                    item {
                        TestLedRow(
                            appText("test_led", "Test (3 s)"),
                            appText("test_timer_description", "Plays the timer effect once")
                        ) { test(LedResolver.Kind.TIMER) }
                    }
                }
            }
        }
        if (cfg.enabled) {
            item {
                SettingsGroup(title = appText("schedule_time", "SCHEDULE TIME")) {
                    item {
                        SettingsRow(appText("start_time", "Start Time (Turn ON)"), supporting = time(cfg.startHour, cfg.startMinute), onClick = {
                            if (glassDialogs) picker = TimerPicker.Start
                            else TimePickerDialog(context, { _, h, m -> viewModel.setTimerStartTime(h, m) }, cfg.startHour, cfg.startMinute, true).show()
                        })
                    }
                    item {
                        SettingsRow(appText("end_time", "End Time (Turn OFF)"), supporting = time(cfg.endHour, cfg.endMinute), onClick = {
                            if (glassDialogs) picker = TimerPicker.End
                            else TimePickerDialog(context, { _, h, m -> viewModel.setTimerEndTime(h, m) }, cfg.endHour, cfg.endMinute, true).show()
                        })
                    }
                }
            }
            item {
                SettingsGroup(title = appText("timer_days", "REPEAT ON")) {
                    item { DaysOfWeekRow(cfg.daysMask) { dow -> viewModel.setTimerDays(TimerScheduler.toggleDay(cfg.daysMask, dow)) } }
                }
            }
            item {
                SettingsGroup(title = appText("configuration", "CONFIGURATION")) {
                    item {
                        ChoiceRow(
                            uiState.availableEffect.indexOfFirst { it.index.toInt() == cfg.effect }.coerceAtLeast(0),
                            { i -> uiState.availableEffect.getOrNull(i)?.let { viewModel.setTimerEffect(it.index.toInt()) } },
                            uiState.availableEffect, { it.pickerLabel() }, appString(R.string.led_effect_title), appText("effect_during_schedule", "Effect played during the schedule")
                        )
                    }
                    item { SwitchRow(cfg.useOwnValues, { viewModel.setTimerUseOwnValues(it) }, appString(R.string.led_use_own_values_title), appText("customize_color_frequency", "Customize color and frequency")) }
                }
            }
            if (cfg.useOwnValues) {
                item {
                    SettingsGroup(title = appText("led_values", "LED VALUES")) {
                        item {
                            ScrollFieldRow(
                                frequencies.indexOf(cfg.frequency).coerceAtLeast(0),
                                { i -> frequencies.getOrNull(i)?.let(viewModel::setTimerFrequency) },
                                frequencies, { context.getString(R.string.value_hz, it.toString()) }, appString(R.string.led_frq_title), null
                            )
                        }
                        cfg.colors.forEachIndexed { index, color ->
                            item { ColorRow(color, { viewModel.setTimerColor(index, it) }, appString(R.string.led_rgb_title, index + 1), null) }
                        }
                    }
                }
            }
        }
    }

    picker?.let { which ->
        val isStart = which == TimerPicker.Start
        GlassTimePickerDialog(
            title = if (isStart) appText("start_time", "Start Time (Turn ON)") else appText("end_time", "End Time (Turn OFF)"),
            initialHour = if (isStart) cfg.startHour else cfg.endHour,
            initialMinute = if (isStart) cfg.startMinute else cfg.endMinute,
            onConfirm = { h, m ->
                if (isStart) viewModel.setTimerStartTime(h, m) else viewModel.setTimerEndTime(h, m)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
}

private enum class TimerPicker { Start, End }
