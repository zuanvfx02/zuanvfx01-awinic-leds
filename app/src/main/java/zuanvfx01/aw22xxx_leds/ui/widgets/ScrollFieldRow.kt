package zuanvfx01.aw22xxx_leds.ui.widgets

import zuanvfx01.aw22xxx_leds.ui.glass.AppDialog
import zuanvfx01.aw22xxx_leds.ui.utils.appString
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberScrollFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import zuanvfx01.aw22xxx_leds.R

/**
 * Row that opens the Material 3 Expressive [ScrollField] wheel to pick one of [values].
 * Replaces the hand-rolled VerticalPager picker.
 */
@Composable
fun <T> ScrollFieldRow(
    currentValueIndex: Int,
    onValueChange: (Int) -> Unit,
    values: List<T>,
    valueKey: (T) -> String,
    headline: String,
    supporting: String?,
    modifier: Modifier = Modifier,
    supportingMono: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = values.getOrNull(currentValueIndex)

    SettingsRow(
        headline = headline,
        modifier = modifier,
        supporting = supporting,
        supportingMono = supportingMono,
        onClick = { open = true },
        trailing = {
            Text(
                current?.let(valueKey) ?: "—",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    )

    if (open) {
        val state = rememberScrollFieldState(
            itemCount = values.size,
            index = currentValueIndex.coerceIn(0, (values.size - 1).coerceAtLeast(0))
        )

        AppDialog(
            onDismissRequest = { open = false },
            title = headline,
            confirmText = appString(R.string.ok),
            onConfirm = {
                onValueChange(state.selectedOption)
                open = false
            },
            confirmExpressive = true,
            dismissText = appString(R.string.cancel),
            onDismiss = { open = false },
        ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ScrollField(
                        state = state,
                        modifier = Modifier.width(200.dp),
                    ) { index, selected ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                valueKey(values[index]),
                                style = if (selected)
                                    MaterialTheme.typography.headlineMedium
                                else
                                    MaterialTheme.typography.titleLarge,
                                color = if (selected)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Text(
                        appString(R.string.scroll_to_select),
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
        }
    }
}
