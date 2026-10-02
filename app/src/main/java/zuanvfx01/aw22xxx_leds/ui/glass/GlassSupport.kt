package zuanvfx01.aw22xxx_leds.ui.glass

import android.os.Build

/**
 * Feature gate for the "Liquid Glass" UI (library: io.github.kyant0:backdrop).
 *
 *  - Android 11 and below (API < 31): [blur] = false  -> the plain stock Material 3 UI,
 *    no blur, nothing from the glass package is ever composed.
 *  - Android 12 / 12L (API 31-32):     [blur] = true, [lens] = false -> frosted glass
 *    (blur + vibrancy + highlight), without refraction.
 *  - Android 13+ (API 33+):            [blur] = true, [lens] = true  -> full Liquid Glass
 *    (AGSL refraction shader on top of the blur).
 *
 * The values never change during a process, so composables may branch on them freely.
 */
object GlassSupport {
    /** RenderEffect blur is available (Android 12+). Gate for ALL glass UI. */
    val blur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** AGSL RuntimeShader refraction is available (Android 13+). */
    val lens: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
