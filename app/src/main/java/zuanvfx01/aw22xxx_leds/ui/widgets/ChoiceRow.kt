package zuanvfx01.aw22xxx_leds.ui.widgets

import zuanvfx01.aw22xxx_leds.ui.glass.AppDialog
import zuanvfx01.aw22xxx_leds.ui.utils.appString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import zuanvfx01.aw22xxx_leds.R

/**
 * Row that shows the current choice and opens a single-select dialog. The dialog is
 * dismissible via back / scrim (fixes the old non-dismissible Dialog).
 */
@Composable
fun <T> ChoiceRow(
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    current?.let(valueKey) ?: "—",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )

    if (open) {
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = currentValueIndex.coerceAtLeast(0)
        )

        AppDialog(
            onDismissRequest = { open = false },
            title = headline,
            confirmText = appString(R.string.cancel),
            onConfirm = { open = false },
            confirmPrimary = false, // single neutral capsule; on Android 11- it stays the old TextButton
        ) {
                LazyColumn(Modifier.heightIn(max = 420.dp), state = listState) {
                    itemsIndexed(values) { index, value ->
                        val selected = index == currentValueIndex

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onValueChange(index)
                                        open = false
                                    }
                                )
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(valueKey(value), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
        }
    }
}
