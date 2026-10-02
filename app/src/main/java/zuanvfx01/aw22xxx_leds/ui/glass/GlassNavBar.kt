package zuanvfx01.aw22xxx_leds.ui.glass

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import zuanvfx01.aw22xxx_leds.ui.glass.lib.LiquidBottomTab
import zuanvfx01.aw22xxx_leds.ui.glass.lib.LiquidBottomTabs
import zuanvfx01.aw22xxx_leds.ui.glass.lib.LocalLiquidBottomTabsTintLayer
import zuanvfx01.aw22xxx_leds.ui.widgets.FloatingNavBar
import zuanvfx01.aw22xxx_leds.ui.widgets.FloatingNavItem

/**
 * Picks the nav bar for the running device:
 *  - [backdrop] != null (Android 12+)  -> [LiquidBottomTabs], the Kyant AndroidLiquidGlass component
 *    copied as-is (see ui/glass/lib/LiquidBottomTabs.kt)
 *  - [backdrop] == null (Android 11-)  -> the original [FloatingNavBar], untouched.
 */
@Composable
fun AdaptiveFloatingNavBar(
    items: List<FloatingNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    if (backdrop != null && GlassSupport.blur) {
        LiquidFloatingNavBar(items, selectedIndex, onSelect, backdrop, modifier)
    } else {
        FloatingNavBar(items, selectedIndex, onSelect, modifier)
    }
}

/**
 * Thin adapter: feeds the app's [FloatingNavItem]s into the library's [LiquidBottomTabs] /
 * [LiquidBottomTab]. All glass behaviour (drag, refraction, press-to-zoom, highlight) is the library's.
 */
@Composable
private fun LiquidFloatingNavBar(
    items: List<FloatingNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = colors.onSurface
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = contentColor, fontSize = 12.sp)

    // Stable lambda (captures a State, not the Int) so the library's remember(selectedTabIndex) is not
    // reset on every selection change.
    val selected = rememberUpdatedState(selectedIndex)

    LiquidBottomTabs(
        selectedTabIndex = { selected.value },
        onTabSelected = onSelect,
        backdrop = backdrop,
        tabsCount = items.size.coerceAtLeast(1),
        modifier = modifier,
        accentColor = colors.primary,
    ) {
        // The library composes this content twice (visible layer + tinted overlay inside the thumb).
        val isTintLayer = LocalLiquidBottomTabsTintLayer.current
        items.forEachIndexed { index, item ->
            LiquidBottomTab(
                onClick = { onSelect(index) },
                // item.modifier carries onGloballyPositioned for the tutorial: attach it once only.
                modifier = if (isTintLayer) Modifier else item.modifier,
            ) {
                Icon(
                    imageVector = if (index == selected.value) item.selectedIcon else item.unselectedIcon,
                    contentDescription = null, // the label is the accessible name
                    tint = contentColor,
                    modifier = Modifier.size(28.dp),
                )
                BasicText(
                    text = item.label,
                    style = labelStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
