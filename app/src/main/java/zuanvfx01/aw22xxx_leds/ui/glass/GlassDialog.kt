package zuanvfx01.aw22xxx_leds.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.ui.utils.appString

/*
 * iOS-style Liquid Glass dialogs (Android 12+), following DialogContent.kt of Kyant AndroidLiquidGlass.
 *
 * Why an in-window overlay instead of androidx.compose.ui.window.Dialog:
 * a Dialog is a separate Window, so the glass could not sample the app content behind it (the backdrop
 * layer lives in the activity's window). Here every dialog is composed into ONE host that sits on top of
 * the app in the same composition, so it refracts the real screen content.
 *
 * Android 11 and below (or no host in scope) -> the original Material AlertDialog / Dialog, untouched.
 */

/** State of the single overlay host. Provided at the app root via [LocalGlassDialogHost]. */
@Stable
class GlassDialogHostState {
    internal val entries = mutableStateListOf<GlassDialogEntry>()

    /** App content recorded as a backdrop (the NavHost). null -> glass look without content behind it. */
    var backdrop: Backdrop? by mutableStateOf(null)
}

@Stable
internal class GlassDialogEntry {
    var onDismissRequest: () -> Unit by mutableStateOf({})
    var body: @Composable ColumnScope.() -> Unit by mutableStateOf<@Composable ColumnScope.() -> Unit>({})
}

val LocalGlassDialogHost = staticCompositionLocalOf<GlassDialogHostState?> { null }

/** true when dialogs should use the glass overlay on this device / in this composition. */
@Composable
fun rememberGlassDialogsAvailable(): Boolean =
    GlassSupport.blur && LocalGlassDialogHost.current != null

/** Put this once, last, in the root Box of the app so it draws above everything else. */
@Composable
fun GlassDialogHost(state: GlassDialogHostState) {
    val backdrop = state.backdrop ?: emptyBackdrop()
    state.entries.forEach { entry ->
        key(entry) { GlassDialogLayer(entry, backdrop) }
    }
}

@Composable
private fun GlassDialogLayer(entry: GlassDialogEntry, backdrop: Backdrop) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.6f)
        else Color(0xFF121212).copy(0.4f)
    val dimColor =
        if (isLightTheme) Color(0xFF29293A).copy(0.23f)
        else Color(0xFF121212).copy(0.56f)

    BackHandler { entry.onDismissRequest() }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(220),
        label = "glassDialogProgress",
    )

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(dimColor, alpha = progress) }
            // Tap outside the glass = dismiss. This node also blocks touches from reaching the app below.
            .pointerInput(entry) { detectTapGestures { entry.onDismissRequest() } }
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer { alpha = progress }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(48f.dp) },
                    effects = {
                        colorControls(
                            brightness = if (isLightTheme) 0.2f else 0f,
                            saturation = 1.5f
                        )
                        blur(if (isLightTheme) 16f.dp.toPx() else 8f.dp.toPx())
                        lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Plain },
                    // The library demo records the dim as part of the backdrop; do the same so the glass
                    // samples the dimmed screen rather than the bright one.
                    onDrawBackdrop = { drawBackdrop ->
                        drawBackdrop()
                        drawRect(dimColor)
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                // Taps inside the glass must not reach the dismiss handler of the scrim.
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            val scope = this
            // We are outside any Surface, so give Material text a sensible default color.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                entry.body(scope)
            }
        }
    }
}

@Composable
private fun RegisterGlassDialog(
    host: GlassDialogHostState,
    onDismissRequest: () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val entry = remember { GlassDialogEntry() }
    // Keep the host's copy of the lambdas fresh on every recomposition of the call site.
    SideEffect {
        entry.onDismissRequest = onDismissRequest
        entry.body = body
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/**
 * Adaptive dialog used by every dialog in the app.
 *
 *  - Android 12+ : iOS Liquid Glass card (title, body, capsule buttons) in the overlay host.
 *  - Android 11- : the previous Material dialog. [legacyDialog] replaces the default AlertDialog when a
 *    call site used something else (e.g. a plain Dialog + Card).
 *
 * @param confirmPrimary  filled accent capsule (true) or neutral capsule (false)
 * @param confirmExpressive legacy only: the confirm button used ButtonDefaults.shapes() at the call site
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmPrimary: Boolean = true,
    confirmExpressive: Boolean = false,
    dismissText: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    dismissEnabled: Boolean = true,
    legacyDialog: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val host = LocalGlassDialogHost.current
    if (GlassSupport.blur && host != null) {
        RegisterGlassDialog(host, onDismissRequest) {
            GlassDialogBody(
                title = title,
                confirmText = confirmText,
                onConfirm = onConfirm,
                confirmEnabled = confirmEnabled,
                confirmPrimary = confirmPrimary,
                dismissText = dismissText,
                onDismiss = onDismiss,
                dismissEnabled = dismissEnabled,
                content = content,
            )
        }
    } else if (legacyDialog != null) {
        legacyDialog()
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title?.let { { Text(it) } },
            text = content,
            confirmButton = {
                if (confirmPrimary) {
                    if (confirmExpressive) {
                        Button(onClick = onConfirm, enabled = confirmEnabled, shapes = ButtonDefaults.shapes()) { Text(confirmText) }
                    } else {
                        Button(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText) }
                    }
                } else {
                    TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText) }
                }
            },
            dismissButton = dismissText?.let {
                { TextButton(onClick = onDismiss, enabled = dismissEnabled) { Text(it) } }
            },
        )
    }
}

@Composable
private fun ColumnScope.GlassDialogBody(
    title: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    confirmPrimary: Boolean,
    dismissText: String?,
    onDismiss: () -> Unit,
    dismissEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isLightTheme = !isSystemInDarkTheme()
    // Same value the library uses for the secondary (Cancel) capsule: containerColor.copy(0.2f)
    val neutralButton =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.2f)
        else Color(0xFF121212).copy(0.2f)

    if (title != null) {
        Text(
            title,
            Modifier.padding(28f.dp, 24f.dp, 28f.dp, 12f.dp),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 24f.sp, fontWeight = FontWeight.Medium),
        )
    }

    // weight(fill = false): a tall body (color picker, long list) shrinks instead of pushing the buttons off-screen.
    Box(
        Modifier
            .weight(1f, fill = false)
            .padding(24f.dp, if (title != null) 12f.dp else 24f.dp, 24f.dp, 12f.dp)
    ) { content() }

    Row(
        Modifier
            .padding(24f.dp, 12f.dp, 24f.dp, 24f.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dismissText != null) {
            GlassDialogButton(
                text = dismissText,
                onClick = onDismiss,
                enabled = dismissEnabled,
                background = neutralButton,
                textColor = colors.onSurface,
            )
        }
        GlassDialogButton(
            text = confirmText,
            onClick = onConfirm,
            enabled = confirmEnabled,
            background = if (confirmPrimary) colors.primary else neutralButton,
            textColor = if (confirmPrimary) colors.onPrimary else colors.onSurface,
        )
    }
}

@Composable
private fun RowScope.GlassDialogButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    background: Color,
    textColor: Color,
) {
    Row(
        Modifier
            .clip(Capsule())
            .background(background)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .height(48f.dp)
            .weight(1f)
            .padding(horizontal = 16f.dp),
        horizontalArrangement = Arrangement.spacedBy(4f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = textColor,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16f.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Time picker in the glass style (Android 12+). Callers on Android 11- keep using the platform
 * android.app.TimePickerDialog; gate with [rememberGlassDialogsAvailable].
 */
@Composable
fun GlassTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = appString(R.string.ok),
        onConfirm = { onConfirm(state.hour, state.minute) },
        dismissText = appString(R.string.cancel),
        onDismiss = onDismiss,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(state = state)
        }
    }
}
