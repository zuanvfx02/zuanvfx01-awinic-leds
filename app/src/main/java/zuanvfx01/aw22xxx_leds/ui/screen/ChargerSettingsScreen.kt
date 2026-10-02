package zuanvfx01.aw22xxx_leds.ui.screen

import zuanvfx01.aw22xxx_leds.ui.utils.appText
import zuanvfx01.aw22xxx_leds.ui.utils.appString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.services.LedResolver
import zuanvfx01.aw22xxx_leds.ui.model.LedsViewModel
import zuanvfx01.aw22xxx_leds.ui.widgets.ChoiceRow
import zuanvfx01.aw22xxx_leds.ui.widgets.ColorRow
import zuanvfx01.aw22xxx_leds.ui.widgets.ScrollFieldRow
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsGroup
import zuanvfx01.aw22xxx_leds.ui.widgets.SwitchRow
import zuanvfx01.aw22xxx_leds.ui.widgets.TabScaffold
import zuanvfx01.aw22xxx_leds.ui.widgets.TestLedRow
import zuanvfx01.aw22xxx_leds.ui.widgets.pickerLabel
import zuanvfx01.aw22xxx_leds.ui.widgets.rememberLedTester

@Composable
fun ChargerSettingsScreen(viewModel: LedsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val frequencies = remember { (1..100).toList() }
    val lowChoices = remember { (5..50).toList() }
    val highChoices = remember { (51..99).toList() }
    val cfg = uiState.charger
    val ex = uiState.chargerExtras
    val test = rememberLedTester(viewModel)

    TabScaffold(appText("charger_led", "Charger LED"), appText("automation_section", "Automation"), onBack = onBack) {
        item {
            SettingsGroup(title = appText("master_switch", "MASTER SWITCH")) {
                item { SwitchRow(cfg.enabled, { viewModel.setEnableChargerLed(it) }, appText("enable_charger_led", "Enable Charger LED"), appText("charger_led_description", "Turn on LED when device is plugged in")) }
                if (cfg.enabled) {
                    item {
                        TestLedRow(
                            appText("test_led", "Test (3 s)"),
                            appText("test_charger_description", "Plays what the LED shows at the current battery level")
                        ) { test(LedResolver.Kind.CHARGER) }
                    }
                }
            }
        }
        if (cfg.enabled) {
            item {
                SettingsGroup(title = appText("configuration", "CONFIGURATION")) {
                    item {
                        ChoiceRow(
                            uiState.availableEffect.indexOfFirst { it.index.toInt() == cfg.effect }.coerceAtLeast(0),
                            { i -> uiState.availableEffect.getOrNull(i)?.let { viewModel.setChargerEffect(it.index.toInt()) } },
                            uiState.availableEffect, { it.pickerLabel() }, appString(R.string.led_effect_title), appText("effect_while_charging", "Effect played while charging")
                        )
                    }
                    item { SwitchRow(cfg.useOwnValues, { viewModel.setChargerUseOwnValues(it) }, appString(R.string.led_use_own_values_title), appText("customize_color_frequency", "Customize color and frequency")) }
                    item {
                        SwitchRow(
                            ex.onlyScreenOff,
                            { viewModel.setChargerOnlyScreenOff(it) },
                            appText("only_screen_off", "Only when the screen is off"),
                            appText("charger_only_screen_off_description", "LED goes dark while you use the phone")
                        )
                    }
                }
            }
            if (cfg.useOwnValues) {
                item {
                    SettingsGroup(title = appText("led_values", "LED VALUES")) {
                        item {
                            ScrollFieldRow(
                                frequencies.indexOf(cfg.frequency).coerceAtLeast(0),
                                { i -> frequencies.getOrNull(i)?.let(viewModel::setChargerFrequency) },
                                frequencies, { context.getString(R.string.value_hz, it.toString()) }, appString(R.string.led_frq_title), null
                            )
                        }
                        cfg.colors.forEachIndexed { index, color ->
                            item { ColorRow(color, { viewModel.setChargerColor(index, it) }, appString(R.string.led_rgb_title, index + 1), null) }
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = appText("battery_level", "BATTERY LEVEL")) {
                    item {
                        SwitchRow(
                            ex.levelColors,
                            { viewModel.setChargerLevelColors(it) },
                            appText("level_colors", "Color by battery level"),
                            appText("level_colors_description", "Low, high and full battery get their own color. In between, the settings above are used.")
                        )
                    }
                    if (ex.levelColors) {
                        item {
                            ScrollFieldRow(
                                lowChoices.indexOf(ex.lowThreshold).coerceAtLeast(0),
                                { i -> lowChoices.getOrNull(i)?.let(viewModel::setChargerLowThreshold) },
                                lowChoices, { "$it %" },
                                appText("level_low_below", "Low battery below"), null
                            )
                        }
                        item {
                            ColorRow(Color(ex.lowColor), { viewModel.setChargerLowColor(it) }, appText("level_low_color", "Low color"), null)
                        }
                        item {
                            TestLedRow(appText("test_low", "Test low"), null) { test(LedResolver.Kind.CHARGER_LOW) }
                        }
                        item {
                            ScrollFieldRow(
                                highChoices.indexOf(ex.highThreshold).coerceAtLeast(0),
                                { i -> highChoices.getOrNull(i)?.let(viewModel::setChargerHighThreshold) },
                                highChoices, { "$it %" },
                                appText("level_high_from", "High battery from"), null
                            )
                        }
                        item {
                            ColorRow(Color(ex.highColor), { viewModel.setChargerHighColor(it) }, appText("level_high_color", "High color"), null)
                        }
                        item {
                            TestLedRow(appText("test_high", "Test high"), null) { test(LedResolver.Kind.CHARGER_HIGH) }
                        }
                        item {
                            ColorRow(Color(ex.fullColor), { viewModel.setChargerFullColor(it) }, appText("level_full_color", "Full color (100 %)"), null)
                        }
                        item {
                            TestLedRow(appText("test_full", "Test full"), null) { test(LedResolver.Kind.CHARGER_FULL) }
                        }
                    }
                }
            }
        }
    }
}
