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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.ui.platform.LocalContext
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
import zuanvfx01.aw22xxx_leds.services.MusicLedService
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsGroup
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
        scope.launch { Application.INSTANCE.settings.updateData { it.toBuilder().setMusic(enabled).build() } }
        if (!hasMic) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        else {
            startMusicService()
        }
    }

    LaunchedEffect(Unit) {
        onTutorialEnableAction(::enableMusic)
    }

    TabScaffold(appText("music_led", "Music LED"), appText("live_microphone", "Live microphone beat detection"), onBack = onBack) {
        config?.let { cfg ->
            item {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (running) Icons.Filled.GraphicEq else Icons.Filled.MusicNote, null, tint = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (running) appText("music_active", "Music LED Active") else appText("music_ready", "Music LED Ready"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(if (running) appText("music_listening", "Listening to microphone input") else appText("music_ready_description", "Enable to react to sound and beats"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

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

            item {
                SettingsGroup(title = appText("effect_section", "EFFECT")) {
                    item {
                        SwitchRow(cfg.useRandomEffects, { persist(cfg.toBuilder().setUseRandomEffects(it).build()) }, appText("random_effects", "Random effects"), appText("random_effects_description", "Cycle through the selected effects on every beat"))
                    }
                    if (cfg.useRandomEffects) {
                        item {
                            EffectSelector(cfg.enabledEffectsList, { persist(cfg.toBuilder().clearEnabledEffects().addAllEnabledEffects(it).build()) })
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = appText("color_section", "COLOR")) {
                    item {
                        SwitchRow(cfg.useDynamicColors, { persist(cfg.toBuilder().setUseDynamicColors(it).build()) }, appText("dynamic_colors", "Dynamic colors"), appText("dynamic_colors_description", "Shift the LED palette as beats are detected"))
                    }
                    item {
                        SwitchRow(cfg.useOwnValues, { persist(cfg.toBuilder().setUseOwnValues(it).build()) }, "Use own values", appText("own_colors_description", "Use your saved LED colors instead of generated colors"))
                    }
                }
            }

            item { SensitivityPanel(cfg.sensitivity, { persist(cfg.toBuilder().setSensitivity(it).build()) }) }
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
private fun SensitivityPanel(initial: Int, onChanged: (Int) -> Unit) {
    var value by remember(initial.coerceIn(1, 10)) { mutableFloatStateOf(initial.coerceIn(1, 10).toFloat()) }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(appText("beat_sensitivity", "Beat sensitivity"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(appText("beat_sensitivity_description", "Lower = reacts easier · Higher = stronger beats only"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${value.roundToInt()}/10", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Slider(value = value, onValueChange = { value = it }, onValueChangeFinished = { onChanged(value.roundToInt().coerceIn(1, 10)) }, valueRange = 1f..10f, steps = 8)
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

@Composable
private fun EffectSelector(selected: List<Int>, onChanged: (List<Int>) -> Unit) {
    var available by remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(Unit) {
        available = SysFsBridge.IO.availableEffects.map { it.index.toInt() }.distinct()
        if (selected.isEmpty() && available.isNotEmpty()) onChanged(available)
    }
    if (available.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(appText("enabled_effects", "Enabled effects"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(available) { effect ->
                    val active = selected.contains(effect)
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable {
                            onChanged(if (active) selected - effect else selected + effect)
                        }.padding(horizontal = 18.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(effect.toString(), color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
