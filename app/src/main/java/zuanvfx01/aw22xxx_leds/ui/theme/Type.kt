package zuanvfx01.aw22xxx_leds.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive type scale.
 *
 * Two families only: the system sans for everything, and the system monospace for sysfs
 * paths / register dumps (see [MonoFamily]). The Expressive `*Emphasized` styles (heavier
 * weight, tighter tracking) are inherited from the M3 defaults and used for headlines.
 */
private val Sans = FontFamily.Default
val MonoFamily = FontFamily.Monospace

private val Baseline = Typography()

val AppTypography = Typography(
    displayLarge = Baseline.displayLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = Baseline.displayMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    displaySmall = Baseline.displaySmall.copy(fontFamily = Sans, fontWeight = FontWeight.Bold),

    headlineLarge = Baseline.headlineLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 42.sp, letterSpacing = (-0.8).sp),
    headlineMedium = Baseline.headlineMedium.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
    headlineSmall = Baseline.headlineSmall.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),

    titleLarge = Baseline.titleLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    titleMedium = Baseline.titleMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
    titleSmall = Baseline.titleSmall.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),

    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = Baseline.bodyMedium.copy(fontFamily = Sans, lineHeight = 21.sp),
    bodySmall = Baseline.bodySmall.copy(fontFamily = Sans, lineHeight = 18.sp),

    labelLarge = Baseline.labelLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
    labelMedium = Baseline.labelMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
    labelSmall = Baseline.labelSmall.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
)
