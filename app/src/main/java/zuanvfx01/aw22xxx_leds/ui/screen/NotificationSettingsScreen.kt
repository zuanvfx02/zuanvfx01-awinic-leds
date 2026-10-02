package zuanvfx01.aw22xxx_leds.ui.screen

import zuanvfx01.aw22xxx_leds.ui.utils.appText
import zuanvfx01.aw22xxx_leds.ui.utils.appString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.services.LedResolver
import zuanvfx01.aw22xxx_leds.ui.model.LedsViewModel
import zuanvfx01.aw22xxx_leds.ui.widgets.AppIcon
import zuanvfx01.aw22xxx_leds.ui.widgets.AppPickerDialog
import zuanvfx01.aw22xxx_leds.ui.widgets.AppStyleEditorDialog
import zuanvfx01.aw22xxx_leds.ui.widgets.ChoiceRow
import zuanvfx01.aw22xxx_leds.ui.widgets.ColorRow
import zuanvfx01.aw22xxx_leds.ui.widgets.ColorSwatch
import zuanvfx01.aw22xxx_leds.ui.widgets.ScrollFieldRow
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsGroup
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsRow
import zuanvfx01.aw22xxx_leds.ui.widgets.SwitchRow
import zuanvfx01.aw22xxx_leds.ui.widgets.TabScaffold
import zuanvfx01.aw22xxx_leds.ui.widgets.TestLedRow
import zuanvfx01.aw22xxx_leds.ui.widgets.pickerLabel
import zuanvfx01.aw22xxx_leds.ui.widgets.rememberLaunchableApps
import zuanvfx01.aw22xxx_leds.ui.widgets.rememberLedTester

private enum class AppPick { Filter, NewStyle }

@Composable
fun NotificationSettingsScreen(viewModel: LedsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val frequencies = remember { (1..100).toList() }
    val durations = remember { (1..15).toList() }
    val ex = uiState.notificationExtras
    val apps = rememberLaunchableApps()
    val test = rememberLedTester(viewModel)

    var picking by remember { mutableStateOf<AppPick?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }

    fun labelOf(pkg: String): String = apps.firstOrNull { it.packageName == pkg }?.label ?: pkg

    val filterModes = listOf(
        appText("filter_all_apps", "All apps"),
        appText("filter_only_selected", "Only selected apps"),
        appText("filter_all_except", "All apps except selected"),
    )

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
                if (uiState.notification.enabled) {
                    item {
                        TestLedRow(
                            appText("test_led", "Test (3 s)"),
                            appText("test_notification_description", "Plays the current notification effect once")
                        ) { test(LedResolver.Kind.NOTIFICATION) }
                    }
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
                            { it.pickerLabel() },
                            appString(R.string.led_effect_title),
                            appText("notification_effect_description", "Effect played when notification arrives")
                        )
                    }
                    item {
                        ScrollFieldRow(
                            durations.indexOf(ex.durationS).coerceAtLeast(0),
                            { i -> durations.getOrNull(i)?.let(viewModel::setNotificationDuration) },
                            durations,
                            { "$it s" },
                            appText("notification_duration", "Duration"),
                            appText("notification_duration_description", "How long the LED stays on")
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

            item {
                SettingsGroup(title = appText("notification_when", "WHEN TO LIGHT UP")) {
                    item {
                        SwitchRow(
                            ex.onlyScreenOff,
                            { viewModel.setNotificationOnlyScreenOff(it) },
                            appText("only_screen_off", "Only when the screen is off"),
                            appText("only_screen_off_description", "Skip the LED while you are looking at the phone")
                        )
                    }
                    item {
                        SwitchRow(
                            ex.respectDnd,
                            { viewModel.setNotificationRespectDnd(it) },
                            appText("respect_dnd", "Respect Do Not Disturb"),
                            appText("respect_dnd_description", "Stay dark while Do Not Disturb is on")
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = appText("notification_apps", "APPS")) {
                    item {
                        ChoiceRow(
                            ex.filterMode,
                            { viewModel.setNotificationFilterMode(it) },
                            filterModes,
                            { it },
                            appText("notification_filter", "Which apps trigger the LED"),
                            null
                        )
                    }
                    if (ex.filterMode != 0) {
                        item {
                            SettingsRow(
                                headline = appText("choose_apps", "Choose apps"),
                                supporting = String.format(appText("apps_selected", "%d selected"), ex.packages.size),
                                onClick = { picking = AppPick.Filter }
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = appText("app_styles", "COLOR & EFFECT PER APP")) {
                    ex.appStyles.entries.sortedBy { labelOf(it.key).lowercase() }.forEach { (pkg, style) ->
                        item {
                            val effectText = if (style.useEffect) {
                                uiState.availableEffect.firstOrNull { it.index.toInt() == style.effect }?.pickerLabel()
                                    ?: "#${style.effect}"
                            } else appText("app_style_effect_default", "Same as Notification LED")
                            SettingsRow(
                                headline = labelOf(pkg),
                                supporting = effectText,
                                onClick = { editing = pkg },
                                leading = { AppIcon(pkg, 32.dp) },
                                trailing = { if (style.useColor) ColorSwatch(Color(style.color)) }
                            )
                        }
                    }
                    item {
                        SettingsRow(
                            headline = appText("add_app_style", "Add an app…"),
                            supporting = appText("add_app_style_description", "For example WhatsApp = green"),
                            onClick = { picking = AppPick.NewStyle }
                        )
                    }
                }
            }
        }
    }

    when (picking) {
        AppPick.Filter -> AppPickerDialog(
            title = appText("choose_apps", "Choose apps"),
            apps = apps,
            multi = true,
            initialSelection = ex.packages.toSet(),
            onDone = { viewModel.setNotificationPackages(it); picking = null },
            onDismiss = { picking = null },
        )
        AppPick.NewStyle -> AppPickerDialog(
            title = appText("add_app_style", "Add an app…"),
            apps = apps,
            multi = false,
            initialSelection = emptySet(),
            onDone = { chosen -> picking = null; editing = chosen.firstOrNull() },
            onDismiss = { picking = null },
        )
        null -> Unit
    }

    editing?.let { pkg ->
        val removeAction: (() -> Unit)? =
            if (ex.appStyles.containsKey(pkg)) ({ viewModel.setNotificationAppStyle(pkg, null); editing = null }) else null
        AppStyleEditorDialog(
            appLabel = labelOf(pkg),
            initial = ex.appStyles[pkg],
            effects = uiState.availableEffect,
            onSave = { style -> viewModel.setNotificationAppStyle(pkg, style); editing = null },
            onRemove = removeAction,
            onDismiss = { editing = null },
        )
    }
}
