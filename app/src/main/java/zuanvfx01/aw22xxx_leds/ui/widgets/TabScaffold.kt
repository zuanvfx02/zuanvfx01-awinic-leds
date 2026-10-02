package zuanvfx01.aw22xxx_leds.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import zuanvfx01.aw22xxx_leds.ui.glass.GlassSupport
import zuanvfx01.aw22xxx_leds.ui.glass.ProgressiveBlurBar

@Composable
fun TabScaffold(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // The bar floats over the content, so reserve room for it (this already includes the
    // system navigation inset; 0.dp when there is no floating bar).
    val floatingBarPadding = LocalFloatingBarPadding.current

    // Android 12+: the list is recorded as a backdrop and the app bar draws a progressive blur of it
    // (Kyant AndroidLiquidGlass). Android 11-: null -> the stock app bar, nothing extra is composed.
    val listBackdrop = if (GlassSupport.blur) rememberLayerBackdrop() else null
    val baseColor = MaterialTheme.colorScheme.background

    Scaffold(
        modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val appBar: @Composable (TopAppBarColors) -> Unit = { colors ->
                LargeFlexibleTopAppBar(
                    title = { Text(title) },
                    subtitle = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    navigationIcon = if (onBack != null) {
                        { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
                    } else { {} },
                    colors = colors,
                    scrollBehavior = scrollBehavior
                )
            }
            if (listBackdrop != null) {
                ProgressiveBlurBar(backdrop = listBackdrop, tint = baseColor) {
                    // Transparent container: the blur layer behind it provides the surface.
                    appBar(
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        )
                    )
                }
            } else {
                appBar(TopAppBarDefaults.topAppBarColors())
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .then(
                    if (listBackdrop != null) {
                        // layerBackdrop first (outer) so the base color below is recorded too.
                        Modifier.layerBackdrop(listBackdrop).background(baseColor)
                    } else Modifier
                ),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = floatingBarPadding + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content
        )
    }
}
