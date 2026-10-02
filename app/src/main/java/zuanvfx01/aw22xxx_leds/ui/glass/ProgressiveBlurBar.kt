package zuanvfx01.aw22xxx_leds.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

/** Which screen edge the progressive blur is anchored to. The blur is strongest AT that edge. */
enum class BlurEdge { Top, Bottom }

/**
 * AGSL alpha mask based on the Kyant catalog (ProgressiveBlurContent.kt): the blurred backdrop is
 * fully visible next to the anchored edge and fades out through the far half, mixed with a tint.
 * `bottom` > 0.5 mirrors the mask vertically so the same shader serves the top app bar and the
 * bottom navigation bar.
 */
private const val AlphaMaskShader = """
    uniform shader content;
    
    uniform float2 size;
    uniform float bottom;
    layout(color) uniform half4 tint;
    uniform float tintIntensity;
    
    half4 main(float2 coord) {
        float y = bottom > 0.5 ? size.y - coord.y : coord.y;
        float blurAlpha = smoothstep(size.y, size.y * 0.5, y);
        float tintAlpha = smoothstep(size.y, size.y * 0.5, y);
        return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
    }"""

/**
 * Progressive-blur layer for app bars (Android 12+ only; callers must check [GlassSupport.blur]).
 *
 *  - Android 13+ : the library recipe, `drawPlainBackdrop` + `blur(4.dp)` + the AlphaMask shader.
 *  - Android 12  : AGSL is unavailable, so the same look is approximated with the blur and a
 *                  gradient alpha mask (DstIn) + tint overlay.
 *
 * [content] (the app bar) is drawn on top of the blur layer and is NOT masked.
 */
@Composable
fun ProgressiveBlurBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.background,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        Box(Modifier.matchParentSize().progressiveBlur(backdrop, tint, BlurEdge.Top))
        content()
    }
}

/**
 * A bare progressive-blur layer with no content, e.g. the strip behind the floating bottom
 * navigation. Size it from the call site (`height(...)`, `fillMaxWidth()`) and align it to the edge.
 */
@Composable
fun ProgressiveBlurLayer(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    edge: BlurEdge = BlurEdge.Bottom,
    tint: Color = MaterialTheme.colorScheme.background,
    tintIntensity: Float = 0.8f,
) {
    Box(modifier.progressiveBlur(backdrop, tint, edge, tintIntensity))
}

private fun Modifier.progressiveBlur(
    backdrop: Backdrop,
    tint: Color,
    edge: BlurEdge,
    tintIntensity: Float = 0.8f,
): Modifier =
    if (GlassSupport.lens) {
        // Android 13+: the library's own progressive blur.
        this.drawPlainBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = {
                blur(4f.dp.toPx())
                runtimeShaderEffect("AlphaMask", AlphaMaskShader, "content") {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("bottom", if (edge == BlurEdge.Bottom) 1f else 0f)
                    setColorUniform("tint", tint)
                    setFloatUniform("tintIntensity", tintIntensity)
                }
            },
        )
    } else {
        // Android 12 / 12L: no RuntimeShader -> mask the blurred layer with a gradient instead.
        val mask = if (edge == BlurEdge.Bottom) {
            Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Black, 1f to Color.Black)
        } else {
            Brush.verticalGradient(0f to Color.Black, 0.5f to Color.Black, 1f to Color.Transparent)
        }
        this
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = mask, blendMode = BlendMode.DstIn)
            }
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(4f.dp.toPx()) },
                onDrawSurface = { drawRect(tint.copy(alpha = 0.8f)) },
            )
    }
