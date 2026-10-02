package zuanvfx01.aw22xxx_leds.ui.widgets

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import java.text.DateFormatSymbols
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.bridge.LedEffect
import zuanvfx01.aw22xxx_leds.services.LedResolver
import zuanvfx01.aw22xxx_leds.ui.glass.AppDialog
import zuanvfx01.aw22xxx_leds.ui.model.AppStyleUi
import zuanvfx01.aw22xxx_leds.ui.model.LedsViewModel
import zuanvfx01.aw22xxx_leds.ui.utils.TimerScheduler
import zuanvfx01.aw22xxx_leds.ui.utils.appString
import zuanvfx01.aw22xxx_leds.ui.utils.appText

/** "2 · Breathing" instead of the raw firmware file name. Same style as the Music LED effect chips. */
fun LedEffect.pickerLabel(): String = "${index.toInt()} · $displayName"

// ------------------------------------------------------------------------------------------------
// Test button
// ------------------------------------------------------------------------------------------------

@Composable
fun TestLedRow(headline: String, supporting: String?, onClick: () -> Unit) {
    SettingsRow(
        headline = headline,
        supporting = supporting,
        onClick = onClick,
        trailing = {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    )
}

/** Returns a function that plays a 3 s test and tells the user when it could not (Music LED etc.). */
@Composable
fun rememberLedTester(viewModel: LedsViewModel): (LedResolver.Kind) -> Unit {
    val context = LocalContext.current
    val blocked = appText("test_blocked", "Can't test right now: Music LED is running or another test is playing")
    return remember(viewModel, context, blocked) {
        { kind: LedResolver.Kind ->
            viewModel.testLed(kind) { played ->
                if (!played) Toast.makeText(context, blocked, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Installed apps
// ------------------------------------------------------------------------------------------------

data class LaunchableApp(val packageName: String, val label: String)

/** Launcher apps (what the user thinks of as "apps"), loaded off the main thread. */
@Composable
fun rememberLaunchableApps(): List<LaunchableApp> {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) {
            runCatching {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0)
                    .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    return apps
}

@Composable
fun AppIcon(packageName: String, size: Dp = 36.dp) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Spacer(Modifier.size(size))
    }
}

/**
 * App chooser. [multi] = checkboxes + OK (returns the whole selection);
 * single = tapping an app returns just that one and closes.
 */
@Composable
fun AppPickerDialog(
    title: String,
    apps: List<LaunchableApp>,
    multi: Boolean,
    initialSelection: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(initialSelection) }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = if (multi) appString(R.string.ok) else appString(R.string.cancel),
        onConfirm = { if (multi) onDone(selection) else onDismiss() },
        confirmPrimary = multi,
        dismissText = if (multi) appString(R.string.cancel) else null,
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(appText("search_apps", "Search apps")) },
                modifier = Modifier.fillMaxWidth()
            )
            if (apps.isEmpty()) {
                Text(
                    appText("no_apps_found", "Loading apps…"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in selection
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (multi) {
                                    selection = if (checked) selection - app.packageName else selection + app.packageName
                                } else {
                                    onDone(setOf(app.packageName))
                                }
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (multi) Checkbox(checked = checked, onCheckedChange = null)
                        AppIcon(app.packageName)
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Per-app colour + effect editor for the Notification LED. */
@Composable
fun AppStyleEditorDialog(
    appLabel: String,
    initial: AppStyleUi?,
    effects: List<LedEffect>,
    onSave: (AppStyleUi) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var useColor by remember { mutableStateOf(initial?.useColor ?: true) }
    var color by remember { mutableStateOf(initial?.let { Color(it.color) } ?: Color(0xFF25D366)) }
    var effect by remember { mutableStateOf<Int?>(initial?.takeIf { it.useEffect }?.effect) }
    val controller = rememberColorPickerController()

    AppDialog(
        onDismissRequest = onDismiss,
        title = appLabel,
        confirmText = appString(R.string.ok),
        onConfirm = {
            onSave(
                AppStyleUi(
                    useColor = useColor,
                    color = color.toArgb(),
                    useEffect = effect != null,
                    effect = effect ?: 0,
                )
            )
        },
        confirmExpressive = true,
        dismissText = if (onRemove != null) appText("remove", "Remove") else appString(R.string.cancel),
        onDismiss = { if (onRemove != null) onRemove() else onDismiss() },
    ) {
        Column(
            Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(appText("app_style_color", "Own color"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (useColor) ColorSwatch(color)
                Switch(checked = useColor, onCheckedChange = { useColor = it })
            }
            if (useColor) {
                HsvColorPicker(
                    Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    controller,
                    onColorChanged = { env: ColorEnvelope -> color = env.color },
                    initialColor = color
                )
                BrightnessSlider(
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    controller,
                    borderRadius = 16.dp,
                    borderSize = 0.dp,
                    wheelRadius = 14.dp
                )
            }

            Text(
                appText("app_style_effect", "Effect"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            EffectChoiceRow(
                label = appText("app_style_effect_default", "Same as Notification LED"),
                selected = effect == null,
                onClick = { effect = null }
            )
            effects.forEach { e ->
                EffectChoiceRow(
                    label = e.pickerLabel(),
                    selected = effect == e.index.toInt(),
                    onClick = { effect = e.index.toInt() }
                )
            }
        }
    }
}

@Composable
private fun EffectChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

// ------------------------------------------------------------------------------------------------
// Timer: weekday chips
// ------------------------------------------------------------------------------------------------

/** Mon..Sun toggle chips (labels follow the phone language). At least one day always stays selected. */
@Composable
fun DaysOfWeekRow(mask: Int, onToggle: (Int) -> Unit) {
    val names = remember { DateFormatSymbols.getInstance().shortWeekdays } // index 1 = Sunday
    val order = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        order.forEach { dow ->
            val on = TimerScheduler.dayEnabled(mask, dow)
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onToggle(dow) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    names.getOrNull(dow)?.take(3) ?: "?",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
