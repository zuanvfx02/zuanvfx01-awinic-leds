package zuanvfx01.aw22xxx_leds.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.roundToInt

private val TrackWidth = 64.dp
private val TrackHeight = 28.dp
private val ThumbWidth = 40.dp
private val ThumbHeight = 24.dp

/**
 * Liquid-glass toggle: a flat colored track with a glass thumb that refracts the track
 * beneath it. Only compose this when [GlassSupport.blur] is true; callers fall back to
 * Material3 `Switch` otherwise (see SwitchRow).
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val trackBackdrop = rememberLayerBackdrop()
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "glassSwitch",
    )
    val trackColor = lerp(colors.surfaceVariant, colors.primary, progress)
    val travel = TrackWidth - ThumbWidth - 4.dp

    Box(
        modifier = modifier
            .size(TrackWidth, TrackHeight)
            .alpha(if (enabled) 1f else 0.5f)
            .toggleable(
                value = checked,
                enabled = enabled && onCheckedChange != null,
                role = Role.Switch,
                onValueChange = { onCheckedChange?.invoke(it) },
                interactionSource = null,
                indication = null,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Track: this is the layer the thumb refracts.
        Box(
            Modifier
                .size(TrackWidth, TrackHeight)
                .layerBackdrop(trackBackdrop)
                .clip(CircleShape)
                .background(trackColor),
        )

        // Thumb.
        Box(
            Modifier
                .offset { IntOffset((2.dp + travel * progress).roundToPx(), 0) }
                .size(ThumbWidth, ThumbHeight)
                .drawBackdrop(
                    backdrop = trackBackdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(2.dp.toPx())
                        lens(5.dp.toPx(), 10.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = if (GlassSupport.lens) 0.35f else 0.95f))
                    },
                ),
        )
    }
}
