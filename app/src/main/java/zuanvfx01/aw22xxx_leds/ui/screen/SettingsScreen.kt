package zuanvfx01.aw22xxx_leds.ui.screen

import zuanvfx01.aw22xxx_leds.ui.utils.appText
import zuanvfx01.aw22xxx_leds.ui.utils.appString
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.ui.model.LedsViewModel
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.ui.widgets.ChoiceRow
import zuanvfx01.aw22xxx_leds.ui.widgets.ColorRow
import zuanvfx01.aw22xxx_leds.ui.widgets.ScrollFieldRow
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsGroup
import zuanvfx01.aw22xxx_leds.ui.widgets.SwitchRow
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsRow
import zuanvfx01.aw22xxx_leds.ui.widgets.TabScaffold

@Composable
fun SettingsScreen(
    viewModel: LedsViewModel,
    onNavigateNotification: () -> Unit = {},
    onNavigateCharger: () -> Unit = {},
    onNavigateTimer: () -> Unit = {},
    onNavigateMusic: () -> Unit = {},
    onMusicTutorialTarget: (Rect) -> Unit = {}
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val availableFrequencies = remember { (1..100).toList() }

    // Notification listener access is granted in Android Settings. Re-check it whenever
    // this screen resumes so returning from Settings immediately reflects the new state
    // (and does not require killing/reopening the app).
    var hasNotificationAccess by remember {
        mutableStateOf(
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = NotificationManagerCompat
                    .getEnabledListenerPackages(context)
                    .contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isAutomationActive = uiState.notification.enabled || uiState.charger.enabled || uiState.timer.enabled

    TabScaffold(
        title = appString(R.string.nav_settings),
        subtitle = appString(R.string.app_name)
    ) {
        item {
            SettingsGroup(title = appText("led_section", "LED")) {
                item {
                    SwitchRow(
                        checked = uiState.enabled,
                        onCheckedChange = { viewModel.setEnabled(it) },
                        headline = appString(R.string.led_hwen_title),
                        supporting = appText("manual_led_description", "Turn the LED on manually. Automation does not change this switch.")
                    )
                }
                item {
                    ChoiceRow(
                        currentValueIndex = uiState.availableEffect.indexOfFirst { it.index.toInt() == uiState.currentEffect }.coerceAtLeast(0),
                        onValueChange = { index ->
                            uiState.availableEffect.getOrNull(index)?.let { effect ->
                                viewModel.setEffect(effect.index.toUByte()) 
                            }
                        },
                        values = uiState.availableEffect,
                        valueKey = { it.name },
                        headline = appString(R.string.led_effect_title),
                        supporting = null
                    )
                }
            }
        }
        
        item {
            SettingsGroup(title = appText("automation_section", "Automation")) {
                item {
                    SwitchRow(
                        checked = uiState.smartOverride,
                        onCheckedChange = { viewModel.setSmartOverride(it) },
                        headline = appText("smart_priority", "Smart Priority Mode"),
                        supporting = appText("smart_priority_description", "Automation may temporarily take control of the LED while preserving the Manual switch state.")
                    )
                }
                item {
                    SettingsRow(
                        headline = appText("notification_led", "Notification LED"),
                        supporting = if (!hasNotificationAccess) appText("requires_notification_access", "Requires notification access") else if (uiState.notification.enabled) appText("enabled", "Enabled") else appText("disabled", "Disabled"),
                        onClick = {
                            if (!hasNotificationAccess) {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            } else {
                                onNavigateNotification()
                            }
                        }
                    )
                }
                item {
                    SettingsRow(
                        headline = appText("charger_led", "Charger LED"),
                        supporting = if (uiState.charger.enabled) appText("enabled", "Enabled") else appText("disabled", "Disabled"),
                        onClick = onNavigateCharger
                    )
                }
                item {
                    SettingsRow(
                        headline = appText("timer_schedule", "Timer Schedule"),
                        supporting = if (uiState.timer.enabled) appText("enabled", "Enabled") else appText("disabled", "Disabled"),
                        onClick = onNavigateTimer
                    )
                }
                item {
                    SettingsRow(
                        headline = appText("music_led", "Music LED"),
                        supporting = appText("music_follow_description", "LED follows the rhythm of music"),
                        modifier = Modifier.onGloballyPositioned { onMusicTutorialTarget(it.boundsInRoot()) },
                        onClick = onNavigateMusic
                    )
                }
            }
        }

        item {
            SettingsGroup(title = appString(R.string.cat_application)) {
                item {
                    SwitchRow(
                        checked = uiState.useSavedSettings,
                        onCheckedChange = { viewModel.setUseSavedSettings(it) },
                        headline = appString(R.string.led_use_saved_settings_title),
                        supporting = appString(R.string.led_use_saved_settings_description)
                    )
                }
                item {
                    SwitchRow(
                        checked = uiState.useOwnValues,
                        onCheckedChange = { viewModel.setUseOwnValues(it) },
                        headline = appString(R.string.led_use_own_values_title),
                        supporting = appString(R.string.led_use_own_values_description)
                    )
                }
            }
        }

        if (uiState.useOwnValues) {
            item {
                SettingsGroup(title = appString(R.string.cat_own_values)) {
                    if (SysFsBridge.supports("frq") || !SysFsBridge.isPresent) {
                        item {
                            ScrollFieldRow(
                                currentValueIndex = availableFrequencies.indexOf(uiState.frequency).coerceAtLeast(0),
                                onValueChange = { index ->
                                    availableFrequencies.getOrNull(index)?.let { freq -> viewModel.setFrequency(freq) }
                                },
                                values = availableFrequencies,
                                valueKey = { context.getString(R.string.value_hz, it.toString()) },
                                headline = appString(R.string.led_frq_title),
                                supporting = null
                            )
                        }
                    }

                    uiState.colors.forEachIndexed { index, color ->
                        item {
                            ColorRow(
                                value = color,
                                onValueChange = { newColor -> 
                                    viewModel.setColor(index.toUByte(), newColor) 
                                },
                                headline = appString(R.string.led_rgb_title, index + 1),
                                supporting = null,
                                supportingMono = true
                            )
                        }
                    }
                }
            }
        }
    }
}
