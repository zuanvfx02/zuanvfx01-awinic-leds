package zuanvfx01.aw22xxx_leds.ui.widgets

import zuanvfx01.aw22xxx_leds.ui.glass.AppDialog
import zuanvfx01.aw22xxx_leds.ui.utils.appString
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.ui.theme.MonoFamily

fun Color.toHex(): String = "#%06X".format(0xFFFFFF and toArgb())

/**
 * Round LED swatch with a subtle outline so very light / dark colors stay visible.
 */
@Composable
fun ColorSwatch(color: Color, modifier: Modifier = Modifier, size: Int = 28) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}

/**
 * Row showing a color swatch; tapping opens an HSV picker dialog with live hex preview.
 */
@Composable
fun ColorRow(
    value: Color,
    onValueChange: (Color) -> Unit,
    headline: String,
    supporting: String?,
    modifier: Modifier = Modifier,
    supportingMono: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }

    SettingsRow(
        headline = headline,
        modifier = modifier,
        supporting = supporting,
        supportingMono = supportingMono,
        onClick = { open = true },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    value.toHex(),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColorSwatch(value)
            }
        }
    )

    if (open) {
        var draft by remember { mutableStateOf(value) }
        val controller = rememberColorPickerController()

        AppDialog(
            onDismissRequest = { open = false },
            title = headline,
            confirmText = appString(R.string.ok),
            onConfirm = {
                onValueChange(draft)
                open = false
            },
            confirmExpressive = true,
            dismissText = appString(R.string.cancel),
            onDismiss = { open = false },
        ) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Live preview: before -> after
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorSwatch(value, size = 36)
                        Text(
                            "→",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ColorSwatch(draft, size = 36)
                        Text(
                            draft.toHex(),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFamily)
                        )
                    }

                    HsvColorPicker(
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        controller,
                        onColorChanged = { env: ColorEnvelope -> draft = env.color },
                        initialColor = value
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
        }
    }
}
