package zuanvfx01.aw22xxx_leds.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    headline: String,
    supporting: String?,
    modifier: Modifier = Modifier,
    supportingMono: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    SettingsRow(
        headline = headline,
        modifier = modifier,
        supporting = supporting,
        supportingMono = supportingMono,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        leading = icon?.let {
            { Icon(it, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
        },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                thumbContent = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline
                )
            )
        }
    )
}
