package zuanvfx01.aw22xxx_leds.ui.screen

import zuanvfx01.aw22xxx_leds.ui.utils.appText
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.MusicLedConfig
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.bridge.LedEffect
import androidx.compose.ui.text.style.TextOverflow
import zuanvfx01.aw22xxx_leds.services.DynamicColorGuard
import zuanvfx01.aw22xxx_leds.services.BeatMode
import zuanvfx01.aw22xxx_leds.services.MusicLedService
import zuanvfx01.aw22xxx_leds.ui.widgets.BeatDetectionCard
import zuanvfx01.aw22xxx_leds.ui.widgets.LedEventsCard
import zuanvfx01.aw22xxx_leds.ui.widgets.LedResponseCard
import zuanvfx01.aw22xxx_leds.ui.widgets.MusicStatusCard
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsGroup
import zuanvfx01.aw22xxx_leds.ui.widgets.SoundLevelCard
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsRow
import zuanvfx01.aw22xxx_leds.ui.widgets.SwitchRow
import zuanvfx01.aw22xxx_leds.ui.widgets.TabScaffold
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun MusicLedScreen(
    onBack: () -> Unit,
    onTutorialEnableTarget: (Rect) -> Unit = {},
    onTutorialEnableAction: ((() -> Unit)) -> Unit = {},
    onTutorialEnabled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<MusicLedConfig?>(null) }
    var running by remember { mutableStateOf(false) }
    var hasMic by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            running = MusicLedService.isRunning(context)
            delay(800)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The foreground service can still run if the user declines notifications,
        // but Android may hide its notification from the notification drawer.
        if (config?.enabled == true && hasMic) {
            MusicLedService.start(context)
            running = true
        }
    }

    fun startMusicService() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            MusicLedService.start(context)
            running = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        if (granted && config?.enabled == true) {
            startMusicService()
        } else if (!granted) {
            config?.let { current ->
                val disabled = current.toBuilder().setEnabled(false).build()
                config = disabled
                scope.launch { Application.INSTANCE.settings.updateData { it.toBuilder().setMusic(disabled).build() } }
            }
        }
    }

    LaunchedEffect(Unit) {
        val raw = Application.INSTANCE.settings.data.first().music
        val minF = raw.minFrequency.coerceIn(1, 100).let { if (raw.minFrequency == 0) 10 else it }
        val maxF = raw.maxFrequency.coerceIn(1, 100).let { if (raw.maxFrequency == 0) 80 else it }
        val safe = raw.toBuilder()
            .setSensitivity(if (raw.sensitivity == 0) 5 else raw.sensitivity.coerceIn(1, 10))
            .setMinFrequency(minF)
            .setMaxFrequency(max(minF, maxF))
            .build()
        if (safe != raw) Application.INSTANCE.settings.updateData { it.toBuilder().setMusic(safe).build() }
        config = safe
    }

    fun persist(newConfig: MusicLedConfig) {
        config = newConfig
        scope.launch { Application.INSTANCE.settings.updateData { it.toBuilder().setMusic(newConfig).build() } }
        if (!newConfig.enabled) {
            MusicLedService.stop(context)
            running = false
        }
    }

    fun enableMusic() {
        val current = config ?: return
        val enabled = current.toBuilder().setEnabled(true).build()
        config = enabled
        // Commit first, then start the service. This fixes a genuine DataStore race
        // without touching the proven MusicLedService hardware path.
        scope.launch {
            Application.INSTANCE.settings.updateData {
                it.toBuilder().setMusic(enabled).build()
            }
            if (!hasMic) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else startMusicService()
        }
    }

    LaunchedEffect(Unit) {
        onTutorialEnableAction(::enableMusic)
    }

    TabScaffold(appText("music_led", "Music LED"), appText("live_microphone", "Live microphone beat detection"), onBack = onBack) {
        config?.let { cfg ->
            // Live status: state, BPM, confidence and the Mic -> Flux -> Threshold -> LED pipeline.
            item { MusicStatusCard() }

            item {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(appText("high_battery_usage", "High battery usage"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            appText("music_battery_description", "Music LED can use noticeably more battery than normal LED control. It keeps the microphone active, runs continuous audio analysis in a foreground service, and may write LED settings repeatedly while music is playing."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            appText("music_battery_tip", "Tip: turn it off when you are finished. The notification above stays available while the service is running so you can stop it quickly."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = appText("music_section", "MUSIC LED")) {
                    item {
                        SwitchRow(
                            cfg.enabled,
                            { checked ->
                                if (checked) {
                                    enableMusic()
                                    onTutorialEnabled()
                                } else {
                                    persist(cfg.toBuilder().setEnabled(false).build())
                                }
                            },
                            appText("enable_music_led", "Enable Music LED"),
                            appText("music_react_description", "React to sound captured by the device microphone"),
                            modifier = Modifier.onGloballyPositioned { onTutorialEnableTarget(it.boundsInRoot()) },
                            icon = Icons.Filled.MusicNote
                        )
                    }
                    item {
                        SettingsRow(
                            "Microphone permission",
                            supporting = if (hasMic) appText("microphone_granted", "Granted · ready to capture audio") else appText("microphone_required", "Required before Music LED can start"),
                            leading = { Icon(Icons.Filled.Mic, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { if (!hasMic) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        )
                    }
                }
            }

            if (cfg.enabled && !hasMic) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(appText("microphone_permission_needed", "Microphone permission needed"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(appText("microphone_permission_description", "Android requires microphone access for this test mode."), color = MaterialTheme.colorScheme.onErrorContainer)
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }, shapes = ButtonDefaults.shapes()) { Text(appText("allow_microphone", "Allow microphone")) }
                        }
                    }
                }
            }

            if (cfg.enabled && hasMic && !running) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(appText("audio_capture_not_running", "Audio capture is not running"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(appText("start_service_description", "Start the foreground service to begin beat detection."), color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Button(onClick = { startMusicService() }, shapes = ButtonDefaults.shapes()) { Text(appText("start_detection", "Start detection")) }
                        }
                    }
                }
            }

            // ---- Live dashboard: sound level, how beats are detected, LED response, change log ----
            item { SoundLevelCard() }
            val beatMode = BeatMode.fromId(cfg.beatMode)
            item {
                BeatDetectionCard(
                    hintKey = if (beatMode == BeatMode.AGGRESSIVE) "dash_detect_hint_aggressive" else "dash_detect_hint"
                )
            }
            item {
                SensitivityPanel(
                    cfg.sensitivity,
                    { persist(cfg.toBuilder().setSensitivity(it).build()) },
                    descriptionKey = if (beatMode == BeatMode.AGGRESSIVE) "beat_sensitivity_description_aggressive" else "beat_sensitivity_description",
                    descriptionFallback = if (beatMode == BeatMode.AGGRESSIVE) "Lower = stricter · Higher = reacts to more sounds" else "Lower = reacts easier · Higher = stronger beats only",
                )
            }
            item {
                BeatModePanel(beatMode) { persist(cfg.toBuilder().setBeatMode(it.id).build()) }
            }
            item { LedResponseCard() }
            item { LedEventsCard() }

            item {
                SettingsGroup(title = appText("effect_section", "EFFECT")) {
                    item {
                        SwitchRow(cfg.useRandomEffects, { persist(cfg.toBuilder().setUseRandomEffects(it).build()) }, appText("random_effects", "Random effects"), appText("random_effects_description", "Cycle through the selected effects on every beat"))
                    }
                    if (cfg.useRandomEffects) {
                        item {
                            EffectSelector(
                                selected = cfg.enabledEffectsList,
                                onChanged = { persist(cfg.toBuilder().clearEnabledEffects().addAllEnabledEffects(it).build()) },
                                showNames = cfg.effectLabelMode == 0,
                                onShowNamesChanged = { names -> persist(cfg.toBuilder().setEffectLabelMode(if (names) 0 else 1).build()) },
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = appText("color_section", "COLOR")) {
                    item {
                        // Dynamic colors needs per-LED colour control and a known LED count, and is
                        // switched off for good on phones that rebooted while it was running.
                        val dynamicSupported = remember { SysFsBridge.supports("rgb") && SysFsBridge.colorControlUsable }
                        var dynamicBlocked by remember { mutableStateOf(DynamicColorGuard.isBlocked(context)) }
                        val dynamicUsable = dynamicSupported && !dynamicBlocked
                        LaunchedEffect(dynamicUsable, cfg.useDynamicColors) {
                            if (!dynamicUsable && cfg.useDynamicColors) {
                                persist(cfg.toBuilder().setUseDynamicColors(false).build())
                            }
                        }
                        SwitchRow(
                            checked = cfg.useDynamicColors && dynamicUsable,
                            onCheckedChange = { want ->
                                if (dynamicBlocked && want) {
                                    DynamicColorGuard.clearBlock(context)
                                    dynamicBlocked = false
                                }
                                persist(cfg.toBuilder().setUseDynamicColors(want).build())
                            },
                            headline = appText("dynamic_colors", "Dynamic colors"),
                            supporting = when {
                                dynamicBlocked -> appText("dynamic_colors_blocked", "Turned off: this phone rebooted when Dynamic colors was used. Tap to try again.")
                                !dynamicSupported -> appText("dynamic_colors_unsupported", "Not available: this LED does not expose per-LED colour control (white-only or unreadable).")
                                else -> appText("dynamic_colors_description", "Shift the LED palette as beats are detected")
                            },
                            enabled = dynamicUsable || dynamicBlocked,
                        )
                    }
                    item {
                        SwitchRow(cfg.useOwnValues, { persist(cfg.toBuilder().setUseOwnValues(it).build()) }, "Use own values", appText("own_colors_description", "Use your saved LED colors instead of generated colors"))
                    }
                }
            }

            item {
                FrequencyPanel(
                    cfg.minFrequency,
                    cfg.maxFrequency,
                    { persist(cfg.toBuilder().setMinFrequency(it).build()) },
                    { persist(cfg.toBuilder().setMaxFrequency(it).build()) }
                )
            }

            item {
                SettingsGroup(title = appText("how_it_works", "HOW IT WORKS")) {
                    item {
                        SettingsRow(appText("music_pipeline", "Microphone → detector → LED"), supporting = appText("music_pipeline_description", "Audio energy is measured continuously, beats trigger effects, and silence fades the LED back out."))
                    }
                    item {
                        SettingsRow(appText("foreground_service", "Foreground service"), supporting = appText("foreground_service_description", "The detector can keep running while you browse other screens."))
                    }
                }
            }
        }
    }
}

@Composable
private fun SensitivityPanel(
    initial: Int,
    onChanged: (Int) -> Unit,
    descriptionKey: String = "beat_sensitivity_description",
    descriptionFallback: String = "Lower = reacts easier · Higher = stronger beats only",
) {
    var value by remember(initial.coerceIn(1, 10)) { mutableFloatStateOf(initial.coerceIn(1, 10).toFloat()) }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(appText("beat_sensitivity", "Beat sensitivity"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(appText(descriptionKey, descriptionFallback), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${value.roundToInt()}/10", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Slider(value = value, onValueChange = { value = it }, onValueChangeFinished = { onChanged(value.roundToInt().coerceIn(1, 10)) }, valueRange = 1f..10f, steps = 8)
        }
    }
}

/** Beat detection mode picker: three selectable cards, each with its own icon. */
@Composable
private fun BeatModePanel(selected: BeatMode, onSelected: (BeatMode) -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(appText("beat_mode_title", "Beat detection mode"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                appText("beat_mode_description", "Choose how the detector balances rhythmic precision and fast musical reaction."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BeatModeOption(
                icon = Icons.Filled.GpsFixed,
                title = appText("beat_mode_precision", "Precision"),
                tag = appText("beat_mode_precision_tag", "Tempo-first"),
                description = appText("beat_mode_precision_description", "Follows the detected beat grid and avoids most transient triggers."),
                selected = selected == BeatMode.PRECISION,
                onClick = { onSelected(BeatMode.PRECISION) }
            )
            BeatModeOption(
                icon = Icons.Filled.FlashOn,
                title = appText("beat_mode_aggressive", "Aggressive"),
                tag = appText("beat_mode_aggressive_tag", "V5-style"),
                description = appText("beat_mode_aggressive_description", "Responds quickly to strong kicks, snares and musical transients."),
                selected = selected == BeatMode.AGGRESSIVE,
                onClick = { onSelected(BeatMode.AGGRESSIVE) }
            )
            BeatModeOption(
                icon = Icons.Filled.Tune,
                title = appText("beat_mode_hybrid", "Hybrid"),
                tag = appText("beat_mode_hybrid_tag", "Recommended balance"),
                description = appText("beat_mode_hybrid_description", "Combines tempo accuracy with strong-transient responsiveness."),
                selected = selected == BeatMode.HYBRID,
                onClick = { onSelected(BeatMode.HYBRID) }
            )
        }
    }
}

@Composable
private fun BeatModeOption(
    icon: ImageVector,
    title: String,
    tag: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = shape,
        color = if (selected) cs.primaryContainer else cs.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) cs.primary else cs.outlineVariant
        )
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (selected) cs.primary else cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) cs.onPrimaryContainer else cs.onSurface
                )
                Text(tag, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun FrequencyPanel(minInitial: Int, maxInitial: Int, onMinChanged: (Int) -> Unit, onMaxChanged: (Int) -> Unit) {
    val safeMin = min(minInitial.coerceIn(1, 100), maxInitial.coerceIn(1, 100))
    val safeMax = max(minInitial.coerceIn(1, 100), maxInitial.coerceIn(1, 100))
    var minValue by remember(safeMin, safeMax) { mutableFloatStateOf(safeMin.toFloat()) }
    var maxValue by remember(safeMin, safeMax) { mutableFloatStateOf(safeMax.toFloat()) }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(appText("led_frequency_range", "LED frequency range"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(appText("led_frequency_description", "Controls the effect frequency, not the microphone audio range."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(appText("minimum_frequency", "Minimum · %d Hz").format(minValue.roundToInt()), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Slider(value = minValue, onValueChange = { minValue = it.coerceIn(1f, maxValue) }, onValueChangeFinished = { onMinChanged(minValue.roundToInt()) }, valueRange = 1f..100f, steps = 98)
            Text(appText("maximum_frequency", "Maximum · %d Hz").format(maxValue.roundToInt()), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Slider(value = maxValue, onValueChange = { maxValue = it.coerceIn(minValue, 100f) }, onValueChangeFinished = { onMaxChanged(maxValue.roundToInt()) }, valueRange = 1f..100f, steps = 98)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffectSelector(
    selected: List<Int>,
    onChanged: (List<Int>) -> Unit,
    showNames: Boolean,
    onShowNamesChanged: (Boolean) -> Unit,
) {
    var effects by remember { mutableStateOf<List<LedEffect>>(emptyList()) }
    LaunchedEffect(Unit) {
        effects = SysFsBridge.IO.availableEffects.distinctBy { it.index }
        val all = effects.map { it.index.toInt() }
        if (selected.isEmpty() && all.isNotEmpty()) onChanged(all)
    }
    if (effects.isEmpty()) return

    // If the kernel gives no usable names, names mode would just repeat the numbers.
    val hasNames = effects.any { it.displayName.isNotBlank() }
    val namesMode = showNames && hasNames

    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                appText("enabled_effects", "Enabled effects"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (hasNames) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    LabelModePill(appText("effect_label_numbers", "123"), selected = !namesMode) { onShowNamesChanged(false) }
                    LabelModePill(appText("effect_label_names", "Names"), selected = namesMode) { onShowNamesChanged(true) }
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            effects.forEach { effect ->
                val id = effect.index.toInt()
                val active = selected.contains(id)
                val label = if (namesMode && effect.displayName.isNotBlank()) "$id · ${effect.displayName}" else id.toString()
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onChanged(if (active) selected - id else selected + id) }
                        .padding(horizontal = if (namesMode) 14.dp else 18.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LabelModePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
