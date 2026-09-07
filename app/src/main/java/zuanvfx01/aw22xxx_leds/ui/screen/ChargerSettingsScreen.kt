package zuanvfx01.aw22xxx_leds.ui.screen

import zuanvfx01.aw22xxx_leds.ui.utils.appText
import zuanvfx01.aw22xxx_leds.ui.utils.appString
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
import zuanvfx01.aw22xxx_leds.ui.widgets.SwitchRow
import zuanvfx01.aw22xxx_leds.ui.widgets.TabScaffold

@Composable
fun ChargerSettingsScreen(viewModel: LedsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val frequencies = remember { (1..100).toList() }
    val cfg = uiState.charger

    TabScaffold(appText("charger_led", "Charger LED"), appText("automation_section", "Automation"), onBack = onBack) {
        item {
            SettingsGroup(title = appText("master_switch", "MASTER SWITCH")) {
                item { SwitchRow(cfg.enabled, { viewModel.setEnableChargerLed(it) }, appText("enable_charger_led", "Enable Charger LED"), appText("charger_led_description", "Turn on LED when device is plugged in")) }
            }
        }
        if (cfg.enabled) {
            item {
                SettingsGroup(title = appText("configuration", "CONFIGURATION")) {
                    item {
                        ChoiceRow(
                            uiState.availableEffect.indexOfFirst { it.index.toInt() == cfg.effect }.coerceAtLeast(0),
                            { i -> uiState.availableEffect.getOrNull(i)?.let { viewModel.setChargerEffect(it.index.toInt()) } },
                            uiState.availableEffect, { it.name }, appString(R.string.led_effect_title), appText("effect_while_charging", "Effect played while charging")
                        )
                    }
                    item { SwitchRow(cfg.useOwnValues, { viewModel.setChargerUseOwnValues(it) }, appString(R.string.led_use_own_values_title), appText("customize_color_frequency", "Customize color and frequency")) }
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
        }
    }
}
