package zuanvfx01.aw22xxx_leds.ui.screen

import zuanvfx01.aw22xxx_leds.ui.utils.appText
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import zuanvfx01.aw22xxx_leds.ui.theme.AppTheme

private data class IntroPage(
    val eyebrowKey: String,
    val eyebrowFallback: String,
    val titleKey: String,
    val titleFallback: String,
    val descriptionKey: String,
    val descriptionFallback: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color,
)

private val introPages = listOf(
    IntroPage(
        eyebrowKey = "intro_eyebrow_controller", eyebrowFallback = "AWINIC LED CONTROLLER",
        titleKey = "intro_title_alive", titleFallback = "Make your LEDs feel alive.",
        descriptionKey = "intro_desc_alive", descriptionFallback = "Control effects, colors, frequency and brightness from one clean Fluent-style interface.",
        icon = Icons.Rounded.Lightbulb,
        accent = Color(0xFF2F6BE5),
    ),
    IntroPage(
        eyebrowKey = "intro_eyebrow_precision", eyebrowFallback = "PRECISION CONTROL",
        titleKey = "intro_title_precision", titleFallback = "Tune every detail.",
        descriptionKey = "intro_desc_precision", descriptionFallback = "Choose your effect, set your own RGB values and keep your preferred settings after reboot.",
        icon = Icons.Rounded.Tune,
        accent = Color(0xFF6C5CE7),
    ),
    IntroPage(
        eyebrowKey = "intro_eyebrow_automation", eyebrowFallback = "SMART AUTOMATION",
        titleKey = "intro_title_automation", titleFallback = "Let the LEDs react for you.",
        descriptionKey = "intro_desc_automation", descriptionFallback = "Notification, charger, timer and music modes can take over automatically when you need them.",
        icon = Icons.Rounded.AutoAwesome,
        accent = Color(0xFF008A78),
    ),
    IntroPage(
        eyebrowKey = "intro_eyebrow_ready", eyebrowFallback = "READY TO START",
        titleKey = "intro_title_ready", titleFallback = "Your setup. Your light.",
        descriptionKey = "intro_desc_ready", descriptionFallback = "The app will check your device for a compatible Awinic LED controller before opening the dashboard.",
        icon = Icons.Rounded.GraphicEq,
        accent = Color(0xFFE08A00),
    ),
)

@Preview(showSystemUi = true, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
@Composable
private fun IntroPreview() {
    AppTheme { IntroScreen {} }
}

/** A short launch splash. It deliberately stays lightweight so it never feels like a loading screen. */
@Composable
fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark(Modifier.scale(pulse))
            Spacer(Modifier.height(22.dp))
            Text("Awinic Leds", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(appText("splash_subtitle", "LED controller"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LogoMark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Text("A", Modifier.padding(horizontal = 17.dp, vertical = 12.dp), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text("L", Modifier.padding(horizontal = 17.dp, vertical = 12.dp), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/**
 * First-run walkthrough: four concise slides. It uses the same palette, shapes, typography and
 * expressive motion as the rest of the application instead of looking like a separate product.
 */
@Composable
fun IntroScreen(onComplete: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var drag by remember { mutableFloatStateOf(0f) }
    val item = introPages[page]

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(page) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> drag += amount },
                    onDragEnd = {
                        when {
                            drag < -90f && page < introPages.lastIndex -> page++
                            drag > 90f && page > 0 -> page--
                        }
                        drag = 0f
                    }
                )
            }
    ) {
        // Soft brand glow. Kept subtle so the app still reads like the existing Fluent UI.
        Box(
            Modifier
                .size(360.dp)
                .align(Alignment.TopCenter)
                .alpha(0.22f)
                .background(
                    Brush.radialGradient(listOf(item.accent.copy(alpha = 0.55f), Color.Transparent)),
                    CircleShape
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (page < introPages.lastIndex) {
                    TextButton(onClick = onComplete) { Text(appText("walkthrough_skip", "Skip")) }
                } else {
                    Spacer(Modifier.height(40.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        (slideInHorizontally { it / 3 } + fadeIn(tween(250))) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut(tween(180)))
                    },
                    label = "walkthrough"
                ) { index ->
                val current = introPages[index]
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    IllustrationCard(current)
                    Spacer(Modifier.height(34.dp))
                    Text(
                        appText(current.eyebrowKey, current.eyebrowFallback),
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp),
                        color = current.accent,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        appText(current.titleKey, current.titleFallback),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        appText(current.descriptionKey, current.descriptionFallback),
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                introPages.indices.forEach { index ->
                    val selected = index == page
                    Box(
                        Modifier
                            .height(8.dp)
                            .width(if (selected) 28.dp else 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) item.accent else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { if (page == introPages.lastIndex) onComplete() else page++ },
                Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = item.accent)
            ) {
                Text(if (page == introPages.lastIndex) appText("walkthrough_get_started", "Get started") else appText("walkthrough_continue", "Continue"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun IllustrationCard(page: IntroPage) {
    val infinite = rememberInfiniteTransition(label = "illustration")
    val glow by infinite.animateFloat(
        0.88f, 1.08f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Surface(
        Modifier.size(250.dp).scale(glow),
        shape = RoundedCornerShape(42.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(142.dp).alpha(0.16f).background(page.accent, CircleShape)
            )
            Surface(
                Modifier.size(104.dp),
                shape = RoundedCornerShape(30.dp),
                color = page.accent.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(page.icon, null, Modifier.size(52.dp), tint = page.accent)
                }
            }
            // Small LED dots make the illustration feel specific to this app.
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) { n ->
                    Box(Modifier.size(if (n == 1) 12.dp else 8.dp).clip(CircleShape).background(page.accent.copy(alpha = if (n == 1) 1f else 0.45f)))
                }
            }
        }
    }
}
