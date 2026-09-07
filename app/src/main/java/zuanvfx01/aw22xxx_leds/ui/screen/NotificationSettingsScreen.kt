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
fun NotificationSettingsScreen(viewModel: LedsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val frequencies = remember { (1..100).toList() }

    TabScaffold(appText("notification_led", "Notification LED"), appText("automation_section", "Automation"), onBack = onBack) {
        item {
            SettingsGroup(title = appText("master_switch", "MASTER SWITCH")) {
                item {
                    SwitchRow(
                        uiState.notification.enabled,
                        { viewModel.setEnableNotificationLed(it) },
                        appText("enable_notification_led", "Enable Notification LED"),
                        appText("notification_led_description", "Turn on LED when a new notification arrives")
                    )
                }
            }
        }
        if (uiState.notification.enabled) {
            item {
                SettingsGroup(title = appText("configuration", "CONFIGURATION")) {
                    item {
                        ChoiceRow(
                            uiState.availableEffect.indexOfFirst { it.index.toInt() == uiState.notification.effect }.coerceAtLeast(0),
                            { i -> uiState.availableEffect.getOrNull(i)?.let { viewModel.setNotificationEffect(it.index.toInt()) } },
                            uiState.availableEffect,
                            { it.name },
                            appString(R.string.led_effect_title),
                            appText("notification_effect_description", "Effect played when notification arrives")
                        )
                    }
                    item {
                        SwitchRow(
                            uiState.notification.useOwnValues,
                            { viewModel.setNotificationUseOwnValues(it) },
                            appString(R.string.led_use_own_values_title),
                            appText("customize_color_frequency", "Customize color and frequency")
                        )
                    }
                }
            }
            if (uiState.notification.useOwnValues) {
                item {
                    SettingsGroup(title = appText("led_values", "LED VALUES")) {
                        item {
                            ScrollFieldRow(
                                frequencies.indexOf(uiState.notification.frequency).coerceAtLeast(0),
                                { i -> frequencies.getOrNull(i)?.let(viewModel::setNotificationFrequency) },
                                frequencies,
                                { context.getString(R.string.value_hz, it.toString()) },
                                appString(R.string.led_frq_title), null
                            )
                        }
                        uiState.notification.colors.forEachIndexed { index, color ->
                            item {
                                ColorRow(color, { viewModel.setNotificationColor(index, it) }, appString(R.string.led_rgb_title, index + 1), null)
                            }
                        }
                    }
                }
            }
        }
    }
}
