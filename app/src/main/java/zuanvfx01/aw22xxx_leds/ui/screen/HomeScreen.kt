package zuanvfx01.aw22xxx_leds.ui.screen

import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import zuanvfx01.aw22xxx_leds.services.LedControlGate
import zuanvfx01.aw22xxx_leds.services.MusicTelemetry
import zuanvfx01.aw22xxx_leds.ui.utils.DashTexts
import zuanvfx01.aw22xxx_leds.ui.utils.TimerScheduler
import zuanvfx01.aw22xxx_leds.ui.utils.rememberDashTexts
import zuanvfx01.aw22xxx_leds.ui.widgets.MusicHomeCard
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsRow
import zuanvfx01.aw22xxx_leds.ui.utils.appString
import zuanvfx01.aw22xxx_leds.ui.utils.appText
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.bridge.LedIMax
import zuanvfx01.aw22xxx_leds.bridge.LedReg
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.ui.model.LedsUiState
import zuanvfx01.aw22xxx_leds.ui.model.LedsViewModel
import zuanvfx01.aw22xxx_leds.ui.theme.MonoFamily
import zuanvfx01.aw22xxx_leds.ui.widgets.ColorSwatch
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsGroup
import zuanvfx01.aw22xxx_leds.ui.widgets.TabScaffold
import zuanvfx01.aw22xxx_leds.ui.widgets.ValueRow
import zuanvfx01.aw22xxx_leds.ui.widgets.toHex

/**
 * "IMax20mA" -> "20 mA". Returns null for [LedIMax.Values.Unknown].
 */
private fun LedIMax.Values.label(): String? =
    if (this == LedIMax.Values.Unknown) null
    else name.removePrefix("IMax").replace("mA", " mA")

/**
 * Home tab: read-only, live (1 s) overview of the controller. Shares the [LedsViewModel] with
 * Settings so there is a single polling loop. Registers and the trigger are read directly
 * from the bridge on each state tick; failures fall back to empty values instead of crashing.
 */
@Composable
fun HomeScreen(viewModel: LedsViewModel, onNavigate: (String) -> Unit = {}) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // Only the tiny summary (running + rounded BPM) is read here; the live 30 fps stream is
    // collected inside MusicHomeCard so Home itself never recomposes per audio frame.
    val musicSummary by MusicTelemetry.summary.collectAsStateWithLifecycle()
    val texts = rememberDashTexts()
    val context = LocalContext.current
    var hasNotificationAccess by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
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
    // Home switches to the music dashboard as soon as Music LED is on.
    val musicMode = uiState.music.enabled || musicSummary.running

    val present = remember(uiState) { SysFsBridge.isPresent }
    val registers = remember(uiState) {
        runCatching { SysFsBridge.IO.registers }.getOrDefault(emptyList())
    }
    val trigger = remember(uiState) {
        runCatching { SysFsBridge.IO.triggers }
            .getOrNull()
            ?.let { (list, selected) -> list.getOrNull(selected)?.trim() }
            ?.takeIf { it.isNotEmpty() }
    }

    val effectName = uiState.availableEffect
        .firstOrNull { it.index.toInt() == uiState.currentEffect }
        ?.displayName
        ?: uiState.availableEffect.getOrNull(uiState.currentEffect)?.displayName

    val dash = appString(R.string.value_none)

    TabScaffold(
        title = appString(R.string.nav_home),
        subtitle = appString(R.string.home_subtitle)
    ) {
        item {
            if (musicMode) {
                MusicHomeCard(onClick = { onNavigate("settings_music") })
            } else {
                HeroCard(uiState, present, effectName)
            }
        }

        item {
            AutomationOverview(
                texts = texts,
                uiState = uiState,
                musicRunning = musicSummary.running,
                musicBpm = musicSummary.bpm,
                hasNotificationAccess = hasNotificationAccess,
                effectName = { index ->
                    uiState.availableEffect.firstOrNull { it.index.toInt() == index }?.displayName ?: "#$index"
                },
                onNavigate = { route ->
                    if (route == "settings_notification" && !hasNotificationAccess) {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } else {
                        onNavigate(route)
                    }
                }
            )
        }

        item {
            SettingsGroup(title = appString(R.string.home_section_controller)) {
                item {
                    ValueRow(
                        appString(R.string.home_status),
                        appString(
                            if (present) R.string.home_status_online
                            else R.string.home_status_offline
                        )
                    )
                }
                item {
                    ValueRow(
                        appString(R.string.home_sysfs_path),
                        SysFsBridge.LED_DIR,
                        valueMono = true
                    )
                }
                if (SysFsBridge.supports("imax") || !SysFsBridge.isPresent) {
                    item {
                        ValueRow(
                            appString(R.string.home_imax),
                            uiState.currentIMax.label() ?: appString(R.string.value_unknown)
                        )
                    }
                }
                if (SysFsBridge.supports("trigger") || !SysFsBridge.isPresent) {
                    item {
                        ValueRow(appString(R.string.home_trigger), trigger ?: dash, valueMono = true)
                    }
                }
            }
        }

        item {
            SettingsGroup(title = appString(R.string.home_section_state)) {
                item {
                    ValueRow(
                        appString(R.string.led_hwen_title),
                        appString(
                            if (uiState.enabled) R.string.home_leds_on else R.string.home_leds_off
                        )
                    )
                }
                item {
                    ValueRow(appString(R.string.home_effect), effectName ?: dash)
                }
                if (SysFsBridge.supports("frq") || !SysFsBridge.isPresent) {
                    item {
                        ValueRow(
                            appString(R.string.home_frequency),
                            appString(R.string.value_hz, uiState.frequency)
                        )
                    }
                }
            }
        }

        item {
            SettingsGroup(
                title = pluralStringResource(
                    R.plurals.home_colors_count,
                    uiState.colors.size,
                    uiState.colors.size
                )
            ) {
                uiState.colors.forEachIndexed { index, color ->
                    item {
                        ValueRow(
                            appString(R.string.led_rgb_title, index),
                            color.toHex(),
                            valueMono = true,
                            leading = { ColorSwatch(color, size = 24) }
                        )
                    }
                }
            }
        }

        if (SysFsBridge.supports("reg") && registers.isNotEmpty()) {
            item {
                SettingsGroup(
                    title = pluralStringResource(
                        R.plurals.home_registers_count,
                        registers.size,
                        registers.size
                    )
                ) {
                    item { RegisterDump(registers) }
                }
            }
        }
    }
}

/**
 * The one "loud" element on the page: a large primary-container card with the effect name
 * as the headline and the live LED colors as a row of swatches.
 */
@Composable
private fun HeroCard(uiState: LedsUiState, present: Boolean, effectName: String?) {
    val colorScheme = MaterialTheme.colorScheme
    val active = present && uiState.enabled

    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (active) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
        contentColor = if (active) colorScheme.onPrimaryContainer else colorScheme.onSurface
    ) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(
                    when {
                        !present -> colorScheme.error
                        uiState.enabled -> colorScheme.primary
                        else -> colorScheme.outline
                    }
                )
                Text(
                    appString(
                        when {
                            !present -> R.string.home_status_offline
                            uiState.enabled -> R.string.home_leds_on
                            else -> R.string.home_leds_off
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(
                effectName ?: appString(R.string.value_none),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                uiState.colors.forEach { ColorSwatch(it, size = 40) }
            }

            Text(
                SysFsBridge.LED_DIR,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                color = LocalContentColorMuted()
            )
        }
    }
}

@Composable
private fun LocalContentColorMuted(): Color =
    MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun StatusDot(color: Color) {
    Surface(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
        color = color,
        shape = CircleShape
    ) {}
}

/**
 * Compact monospace dump: four "II:VV" pairs per line, hex, zero padded.
 */
@Composable
private fun RegisterDump(registers: List<LedReg>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        registers.chunked(4).forEach { line ->
            Text(
                line.joinToString("   ") {
                    "%02X:%02X".format(it.index.toInt(), it.value.toInt())
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class PillTone { On, Running, Off, Warn }

@Composable
private fun StatusPill(text: String, tone: PillTone) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        PillTone.Running -> cs.primary to cs.onPrimary
        PillTone.On -> cs.primaryContainer to cs.onPrimaryContainer
        PillTone.Warn -> cs.errorContainer to cs.onErrorContainer
        PillTone.Off -> cs.surfaceContainerHigh to cs.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = bg, contentColor = fg) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(Modifier.size(6.dp), shape = CircleShape, color = fg) {}
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private fun two(value: Int): String = "%02d".format(value)

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    detail: String,
    pill: String,
    tone: PillTone,
    onClick: () -> Unit,
) {
    SettingsRow(
        headline = title,
        supporting = detail,
        onClick = onClick,
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailing = { StatusPill(pill, tone) }
    )
}

/**
 * One glance at every automation feature: enabled or not, plus the detail that matters
 * (effect + frequency, schedule window, BPM while listening, missing notification access) and
 * which feature currently owns the LED. Rows open the matching settings screen.
 */
@Composable
private fun AutomationOverview(
    texts: DashTexts,
    uiState: LedsUiState,
    musicRunning: Boolean,
    musicBpm: Int,
    hasNotificationAccess: Boolean,
    effectName: (Int) -> String,
    onNavigate: (String) -> Unit,
) {
    // Live: Charger / Timer / Notification / Music publish here whenever they take or release the LED.
    val owner by LedControlGate.ownerFlow.collectAsStateWithLifecycle()
    val ownerText = when {
        musicRunning || owner == LedControlGate.Owner.MUSIC -> texts["home_owner_music"]
        owner == LedControlGate.Owner.NOTIFICATION -> texts["home_owner_notification"]
        owner == LedControlGate.Owner.CHARGER -> texts["home_owner_charger"]
        owner == LedControlGate.Owner.TIMER -> texts["home_owner_timer"]
        else -> texts["home_owner_idle"]
    }
    val ownerBusy = musicRunning || owner != LedControlGate.Owner.NONE

    val musicOn = uiState.music.enabled || musicRunning
    val flags = listOf(
        uiState.smartOverride,
        uiState.notification.enabled,
        musicOn,
        uiState.timer.enabled,
        uiState.charger.enabled
    )
    val on = texts["home_state_enabled"]
    val off = texts["home_state_disabled"]

    SettingsGroup(title = texts["home_overview_title"]) {
        item {
            SettingsRow(
                headline = texts["home_owner_label"],
                supporting = String.format(texts["home_overview_active"], flags.count { it }, flags.size),
                trailing = { StatusPill(ownerText, if (ownerBusy) PillTone.Running else PillTone.Off) }
            )
        }

        // 1. Smart Priority Mode
        item {
            FeatureRow(
                icon = Icons.Filled.AutoAwesome,
                title = appText("smart_priority", "Smart Priority Mode"),
                detail = if (uiState.smartOverride) texts["home_smart_on"] else texts["home_smart_off"],
                pill = if (uiState.smartOverride) on else off,
                tone = if (uiState.smartOverride) PillTone.On else PillTone.Off,
                onClick = { onNavigate("settings") }
            )
        }

        // 2. Notification LED (also needs the notification-listener permission)
        item {
            val enabled = uiState.notification.enabled
            val needsAccess = !hasNotificationAccess
            FeatureRow(
                icon = Icons.Filled.Notifications,
                title = appText("notification_led", "Notification LED"),
                detail = if (needsAccess) {
                    texts["home_notification_access_needed"]
                } else {
                    String.format(
                        texts["home_notification_detail"],
                        effectName(uiState.notification.effect),
                        uiState.notification.frequency
                    )
                },
                pill = when {
                    needsAccess -> texts["home_state_needs_access"]
                    enabled -> on
                    else -> off
                },
                tone = when {
                    needsAccess -> PillTone.Warn
                    enabled -> PillTone.On
                    else -> PillTone.Off
                },
                onClick = { onNavigate("settings_notification") }
            )
        }

        // 3. Music LED
        item {
            val detail = if (musicRunning) {
                texts["home_music_running"] +
                    if (musicBpm > 0) " · " + String.format(texts["home_music_bpm"], musicBpm) else ""
            } else {
                String.format(
                    texts["home_music_ready"],
                    uiState.music.sensitivity,
                    uiState.music.minFrequency,
                    uiState.music.maxFrequency
                )
            }
            FeatureRow(
                icon = Icons.Filled.GraphicEq,
                title = appText("music_led", "Music LED"),
                detail = detail,
                pill = when {
                    musicRunning -> texts["home_state_running"]
                    uiState.music.enabled -> on
                    else -> off
                },
                tone = when {
                    musicRunning -> PillTone.Running
                    uiState.music.enabled -> PillTone.On
                    else -> PillTone.Off
                },
                onClick = { onNavigate("settings_music") }
            )
        }

        // 4. Timer Schedule
        item {
            val t = uiState.timer
            val startsIn = appText("timer_starts_in", "Starts in %s")
            val activeEndsIn = appText("timer_active_ends_in", "Active now · ends in %s")
            val timerRange = String.format(
                texts["home_timer_detail"],
                two(t.startHour) + ":" + two(t.startMinute),
                two(t.endHour) + ":" + two(t.endMinute)
            )
            val timerStatus = if (!t.enabled || (t.startHour == t.endHour && t.startMinute == t.endMinute)) {
                null
            } else if (TimerScheduler.isActiveNow(t.startHour, t.startMinute, t.endHour, t.endMinute, t.daysMask)) {
                String.format(activeEndsIn, TimerScheduler.formatDuration(TimerScheduler.nextEndMillis(t.endHour, t.endMinute) - System.currentTimeMillis()))
            } else {
                String.format(startsIn, TimerScheduler.formatDuration(TimerScheduler.nextStartMillis(t.startHour, t.startMinute, t.daysMask) - System.currentTimeMillis()))
            }
            FeatureRow(
                icon = Icons.Filled.Schedule,
                title = appText("timer_schedule", "Timer Schedule"),
                detail = if (timerStatus != null) timerRange + " · " + timerStatus else timerRange,
                pill = if (t.enabled) on else off,
                tone = if (t.enabled) PillTone.On else PillTone.Off,
                onClick = { onNavigate("settings_timer") }
            )
        }

        // 5. Charger LED
        item {
            val c = uiState.charger
            FeatureRow(
                icon = Icons.Filled.BatteryChargingFull,
                title = appText("charger_led", "Charger LED"),
                detail = String.format(texts["home_charger_detail"], effectName(c.effect), c.frequency),
                pill = if (c.enabled) on else off,
                tone = if (c.enabled) PillTone.On else PillTone.Off,
                onClick = { onNavigate("settings_charger") }
            )
        }
    }
}
