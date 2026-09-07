package zuanvfx01.aw22xxx_leds.ui.screen

import zuanvfx01.aw22xxx_leds.ui.utils.appText
import zuanvfx01.aw22xxx_leds.ui.utils.appString
import android.app.TimePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.ui.model.LedsViewModel
import zuanvfx01.aw22xxx_leds.ui.widgets.ChoiceRow
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

    TabScaffold(appText("timer_schedule", "Timer Schedule"), appText("automation_section", "Automation"), onBack = onBack) {
        item {
            SettingsGroup(title = appText("master_switch", "MASTER SWITCH")) {
                item { SwitchRow(cfg.enabled, { viewModel.setEnableTimerLed(it) }, appText("enable_timer", "Enable Timer"), appText("timer_description", "Automatically control the LED at specific times")) }
            }
        }
        if (cfg.enabled) {
            item {
                SettingsGroup(title = appText("schedule_time", "SCHEDULE TIME")) {
                    item {
                        SettingsRow(appText("start_time", "Start Time (Turn ON)"), supporting = time(cfg.startHour, cfg.startMinute), onClick = {
                            TimePickerDialog(context, { _, h, m -> viewModel.setTimerStartTime(h, m) }, cfg.startHour, cfg.startMinute, true).show()
                        })
                    }
                    item {
                        SettingsRow(appText("end_time", "End Time (Turn OFF)"), supporting = time(cfg.endHour, cfg.endMinute), onClick = {
                            TimePickerDialog(context, { _, h, m -> viewModel.setTimerEndTime(h, m) }, cfg.endHour, cfg.endMinute, true).show()
                        })
                    }
                }
            }
            item {
                SettingsGroup(title = appText("configuration", "CONFIGURATION")) {
                    item {
                        ChoiceRow(
                            uiState.availableEffect.indexOfFirst { it.index.toInt() == cfg.effect }.coerceAtLeast(0),
                            { i -> uiState.availableEffect.getOrNull(i)?.let { viewModel.setTimerEffect(it.index.toInt()) } },
                            uiState.availableEffect, { it.name }, appString(R.string.led_effect_title), appText("effect_during_schedule", "Effect played during the schedule")
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
}
