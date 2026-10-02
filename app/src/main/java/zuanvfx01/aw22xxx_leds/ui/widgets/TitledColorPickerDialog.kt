package zuanvfx01.aw22xxx_leds.ui.widgets

import zuanvfx01.aw22xxx_leds.ui.utils.appString
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import zuanvfx01.aw22xxx_leds.ui.glass.AppDialog
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import zuanvfx01.aw22xxx_leds.R

@Composable
fun TitledColorPickerDialog(
    value: Color,
    onValueChange: (Color) -> Unit,

    title: String,
    description: String?,

    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .clickable { expanded = true },
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)

            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Gray
                )
            }
        }

        Box(
            Modifier
                .size(25.dp)
                .border(1.dp, MaterialTheme.colorScheme.inverseSurface, CircleShape)
                .clip(CircleShape)
                .background(value)
        )
    }

    if (expanded) {
        var color by remember { mutableStateOf(value) }
        val colorPickerController = rememberColorPickerController()

        // Shared by the glass dialog (Android 12+) and the old Dialog + Card (Android 11-).
        val picker: @Composable () -> Unit = {
            HsvColorPicker(
                Modifier
                    .fillMaxWidth()
                    .height(450.dp),
                colorPickerController,
                onColorChanged = { env: ColorEnvelope -> color = env.color },
                initialColor = color
            )

            BrightnessSlider(
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .height(35.dp),
                colorPickerController,
            )
        }

        AppDialog(
            onDismissRequest = { expanded = false },
            title = title,
            confirmText = appString(R.string.ok),
            onConfirm = {
                onValueChange(color)
                expanded = false
            },
            dismissText = appString(R.string.cancel),
            onDismiss = { expanded = false },
            legacyDialog = {
                // Android 11-: unchanged from before.
                Dialog({ expanded = true }) {
                    Card {
                        Column(horizontalAlignment = Alignment.End) {
                            HsvColorPicker(
                                Modifier
                                    .fillMaxWidth()
                                    .height(450.dp),
                                colorPickerController,
                                onColorChanged = { env: ColorEnvelope -> color = env.color },
                                initialColor = color
                            )

                            BrightnessSlider(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                                    .height(35.dp)
                                    .align(Alignment.CenterHorizontally),
                                colorPickerController,
                            )

                            TextButton({
                                onValueChange(color)
                                expanded = false
                            }) {
                                Text(appString(R.string.ok))
                            }
                        }
                    }
                }
            },
        ) {
            Column(Modifier.fillMaxWidth()) { picker() }
        }
    }
}
