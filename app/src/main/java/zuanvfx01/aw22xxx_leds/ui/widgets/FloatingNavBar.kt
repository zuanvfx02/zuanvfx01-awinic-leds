package zuanvfx01.aw22xxx_leds.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Space the floating bar takes at the bottom of the screen (bar + margins + system nav inset).
 * Screens add this to their bottom content padding so the last item can scroll above the bar.
 * Defaults to 0 outside of the main shell.
 */
val LocalFloatingBarPadding = compositionLocalOf { 0.dp }

object FloatingNavBarDefaults {
    val Height = 68.dp

    /** Height of the Android 12+ glass bar (the library's LiquidBottomTabs is fixed at 64dp). */
    val GlassHeight = 64.dp
    val HorizontalMargin = 44.dp
    val BottomMargin = 12.dp

    /** Extra breathing room between the last list item and the bar. */
    val ContentGap = 12.dp
}

data class FloatingNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** Lets the caller attach extra modifiers (e.g. onGloballyPositioned for the tutorial). */
    val modifier: Modifier = Modifier,
)

/**
 * A pill-shaped navigation bar that floats above the content (like the Google app's
 * Search / AI Mode / Speak / Song switcher). The selected item gets its own inner pill.
 *
 * Place it as an overlay (e.g. in a Box, aligned to the bottom) - NOT as a Scaffold bottomBar,
 * otherwise it would be docked to the screen edge instead of floating.
 */
@Composable
fun FloatingNavBar(
    items: List<FloatingNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(FloatingNavBarDefaults.Height),
        shape = CircleShape,
        // Bar = a visibly darker neutral than the page; pill = the lightest surface. They must
        // differ: in this theme surfaceContainer and surfaceContainerLowest are both pure white.
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, item ->
                FloatingNavBarItem(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun FloatingNavBarItem(
    item: FloatingNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Navigation selection must follow the destination synchronously. Animating this
    // background independently from NavHost used to create a second, delayed visual
    // transition on top of the screen transition, which looked like a double flicker.
    val pill = if (selected) colors.surfaceContainerLowest else Color.Transparent
    val tint = colors.onSurface

    Column(
        modifier = modifier
            .then(item.modifier)
            .clip(CircleShape)
            .background(pill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = null, // the label below is the accessible name
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
