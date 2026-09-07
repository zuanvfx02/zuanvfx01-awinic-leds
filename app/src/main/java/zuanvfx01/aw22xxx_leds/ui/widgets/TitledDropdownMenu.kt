package zuanvfx01.aw22xxx_leds.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import zuanvfx01.aw22xxx_leds.ui.theme.AppTheme

@PreviewLightDark()
@Composable
private fun TitledDropdownMenuPreview() {
    AppTheme {
        Surface {
            Column(Modifier.padding(20.dp, 0.dp)) {
                val modifier = Modifier.padding(0.dp, 10.dp)

                val values = listOf("1", "2", "3")

                TitledDropdownMenuMap(0, {}, values, { it }, "Title", "Description", modifier)
                TitledDropdownMenuMap(1, {}, values, { it }, "Title", null, modifier)
            }
        }
    }
}

@Composable
fun <T> TitledDropdownMenuMap(
    currentValueIndex: Int,
    onValueChange: (Int) -> Unit,

    values: List<T>,
    valueKey: (T) -> String,

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

        Box {
            Text(valueKey(values[currentValueIndex]))

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                values.mapIndexed { index, it ->
                    DropdownMenuItem(
                        text = { Text(valueKey(it)) },
                        onClick = {
                            onValueChange(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}