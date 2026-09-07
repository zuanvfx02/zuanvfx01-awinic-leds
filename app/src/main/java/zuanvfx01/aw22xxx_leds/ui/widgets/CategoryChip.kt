package zuanvfx01.aw22xxx_leds.ui.widgets

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import zuanvfx01.aw22xxx_leds.ui.theme.AppTheme

@PreviewLightDark()
@Composable
private fun CategoryChipPreview() {
    AppTheme {
        Surface {
            Column(Modifier.padding(20.dp, 0.dp)) {
                val modifier = Modifier.padding(0.dp, 10.dp)
                CategoryChip("Title", modifier)
            }
        }
    }
}

@Composable
fun CategoryChip(
    name: String,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary

    Box(modifier.border(1.dp, color, RoundedCornerShape(100))) {
        Text(name, Modifier.padding(10.dp, 5.dp), color = color)
    }
}