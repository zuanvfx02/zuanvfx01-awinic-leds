package zuanvfx01.aw22xxx_leds.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import zuanvfx01.aw22xxx_leds.ui.theme.AppTheme

@PreviewLightDark()
@Composable
private fun TitledSwitcherPreview() {
    AppTheme {
        Surface {
            Column(Modifier.padding(20.dp, 0.dp)) {
                val modifier = Modifier.padding(0.dp, 10.dp)
                TitledSwitcher(true, {}, "Title", "Description", modifier)
                TitledSwitcher(false, {}, "Title", "Description", modifier)
                TitledSwitcher(true, {}, "Title", null, modifier)
            }
        }
    }
}

@Composable
fun TitledSwitcher(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,

    title: String,
    description: String?,

    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
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

        Switch(checked, onCheckedChange)
    }
}