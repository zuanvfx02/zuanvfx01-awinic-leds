package zuanvfx01.aw22xxx_leds.ui.widgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import zuanvfx01.aw22xxx_leds.services.MusicTelemetry
import zuanvfx01.aw22xxx_leds.services.MusicTelemetry.Snapshot
import zuanvfx01.aw22xxx_leds.ui.theme.MonoFamily
import zuanvfx01.aw22xxx_leds.ui.utils.DashTexts
import zuanvfx01.aw22xxx_leds.ui.utils.rememberDashTexts
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Music LED dashboard.
 *
 * Performance rule used everywhere in this file: the public "Card" composables are static
 * (they resolve texts once and never read the telemetry value). The telemetry State is passed
 * down and only read inside draw lambdas (Canvas / drawBehind) or small leaf composables, so a
 * 30 fps telemetry stream redraws a few canvases instead of recomposing whole screens.
 */

private const val RELEASE_PER_SECOND = 2.4f   // LED stack fade-out speed (full height in ~0.4 s)
private const val BEAT_FLASH_MS = 180L
private const val MIN_CONFIDENT = 0.42f       // same confidence the tracker needs to predict beats

private val BeatAmber = Color(0xFFFFB300)
private val RampGreen = Color(0xFF2ECC71)
private val RampAmber = Color(0xFFFFC107)
private val RampRed = Color(0xFFFF5252)

@Composable
fun rememberMusicSnapshot(): State<Snapshot> = MusicTelemetry.state.collectAsStateWithLifecycle()

// ---------------------------------------------------------------------------------------
// Public cards
// ---------------------------------------------------------------------------------------

/** Hero card: state, BPM, confidence and the Mic -> Flux -> Threshold -> LED pipeline. */
@Composable
fun MusicStatusCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val texts = rememberDashTexts()
    val state = rememberMusicSnapshot()
    StatusBody(state, texts, modifier, onClick)
}

/** Loudness history (the "voice going up and down") with the activity gate and beat dots. */
@Composable
fun SoundLevelCard(modifier: Modifier = Modifier) {
    val texts = rememberDashTexts()
    val state = rememberMusicSnapshot()
    val cs = MaterialTheme.colorScheme
    DashCard(
        title = texts["dash_level_title"],
        modifier = modifier,
        trailing = { LevelReadout(state, texts) }
    ) {
        SoundLevelChart(state, Modifier.fillMaxWidth().height(132.dp))
        ChartLegend(
            listOf(
                LegendItem(cs.primary, texts["dash_legend_sound"], LegendShape.Dot),
                LegendItem(cs.error, texts["dash_legend_gate"], LegendShape.Dash),
                LegendItem(BeatAmber, texts["dash_legend_beat"], LegendShape.Dot),
            )
        )
        IdleHint(state, texts)
        Hint(texts["dash_level_hint"])
    }
}

/** Shows exactly why/when the LED changes: onset strength vs the adaptive threshold. */
@Composable
fun BeatDetectionCard(modifier: Modifier = Modifier, hintKey: String = "dash_detect_hint") {
    val texts = rememberDashTexts()
    val state = rememberMusicSnapshot()
    val cs = MaterialTheme.colorScheme
    DashCard(
        title = texts["dash_detect_title"],
        modifier = modifier,
        trailing = { RatioChip(state, texts) }
    ) {
        DetectionChart(state, Modifier.fillMaxWidth().height(132.dp))
        ChartLegend(
            listOf(
                LegendItem(cs.primary, texts["dash_legend_flux"], LegendShape.Dot),
                LegendItem(BeatAmber, texts["dash_legend_threshold"], LegendShape.Dash),
                LegendItem(cs.tertiary, texts["dash_legend_trigger"], LegendShape.Bar),
            )
        )
        Hint(texts[hintKey])
    }
}

/** Six named LEDs that light bottom -> top with loudness/beat strength and fade away. */
@Composable
fun LedResponseCard(modifier: Modifier = Modifier) {
    val texts = rememberDashTexts()
    val state = rememberMusicSnapshot()
    val names = remember(texts) { ledNames(texts) }
    DashCard(title = texts["dash_led_title"], modifier = modifier) {
        LedStack(state, names, compact = false)
        Hint(texts["dash_led_hint"])
    }
}

/** The last real LED changes: what triggered them, how strong, which effect/frequency was written. */
@Composable
fun LedEventsCard(modifier: Modifier = Modifier) {
    val texts = rememberDashTexts()
    val state = rememberMusicSnapshot()
    DashCard(title = texts["dash_events_title"], modifier = modifier) {
        EventList(state, texts)
    }
}

/** Compact version for the Home tab: header + small level chart + LED stack. */
@Composable
fun MusicHomeCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val texts = rememberDashTexts()
    val state = rememberMusicSnapshot()
    val names = remember(texts) { ledNames(texts) }
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = cs.surfaceContainerLowest,
        border = BorderStroke(1.dp, cs.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HomeHeader(state, texts)
            SoundLevelChart(state, Modifier.fillMaxWidth().height(76.dp))
            LedStack(state, names, compact = true)
            IdleHint(state, texts)
        }
    }
}

// ---------------------------------------------------------------------------------------
// Status / header
// ---------------------------------------------------------------------------------------

@Composable
private fun StatusBody(
    state: State<Snapshot>,
    texts: DashTexts,
    modifier: Modifier,
    onClick: (() -> Unit)?
) {
    val s = state.value
    val cs = MaterialTheme.colorScheme
    val beatRecent = s.running && s.nowMs - s.lastEventAtMs < BEAT_FLASH_MS
    val label = when {
        !s.running -> texts["dash_state_idle"]
        !s.active -> texts["dash_state_waiting"]
        beatRecent -> texts["dash_state_beat"]
        else -> texts["dash_state_detecting"]
    }
    val dotColor = when {
        !s.running -> cs.outline
        beatRecent -> BeatAmber
        s.active -> cs.primary
        else -> cs.outline
    }
    val confident = s.running && s.confidence >= MIN_CONFIDENT && s.bpm > 0f
    val bpmText = if (confident) s.bpm.roundToInt().toString() else "—"
    val shape = MaterialTheme.shapes.extraLarge

    Surface(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = if (s.running) cs.primaryContainer else cs.surfaceContainerHigh,
        contentColor = if (s.running) cs.onPrimaryContainer else cs.onSurface
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PulseDot(dotColor, beatRecent)
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (s.running) {
                    Text(
                        "${texts["dash_beats"]} ${s.beatCount}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFamily)
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bpmText, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(
                    texts["dash_bpm"],
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${texts["dash_confidence"]} ${(s.confidence.coerceIn(0f, 1f) * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    ProgressBar(if (s.running) s.confidence else 0f, cs.primary, cs.onPrimaryContainer.copy(alpha = 0.15f))
                }
            }

            val stages = listOf(
                texts["dash_stage_mic"] to s.active,
                texts["dash_stage_flux"] to (s.active && s.threshold > 0f && s.flux > 0.5f * s.threshold),
                texts["dash_stage_threshold"] to (s.active && s.ratio >= 1f),
                texts["dash_stage_led"] to beatRecent,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stages.forEachIndexed { index, (name, on) ->
                    StagePill(name, on && s.running)
                    if (index != stages.lastIndex) {
                        Text("›", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(state: State<Snapshot>, texts: DashTexts) {
    val s = state.value
    val cs = MaterialTheme.colorScheme
    val beatRecent = s.running && s.nowMs - s.lastEventAtMs < BEAT_FLASH_MS
    val label = when {
        !s.running -> texts["dash_state_idle"]
        !s.active -> texts["dash_state_waiting"]
        beatRecent -> texts["dash_state_beat"]
        else -> texts["dash_state_detecting"]
    }
    val dot = when {
        !s.running -> cs.outline
        beatRecent -> BeatAmber
        s.active -> cs.primary
        else -> cs.outline
    }
    val confident = s.running && s.confidence >= MIN_CONFIDENT && s.bpm > 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PulseDot(dot, beatRecent)
        Column(Modifier.weight(1f)) {
            Text(
                texts["home_owner_music"],
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Text(
            if (confident) s.bpm.roundToInt().toString() else "—",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = cs.primary
        )
        Text(texts["dash_bpm"], style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun PulseDot(color: Color, pulsing: Boolean) {
    val scale by animateFloatAsState(if (pulsing) 1.7f else 1f, label = "pulseDotScale")
    Box(
        Modifier
            .size(10.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun StagePill(text: String, on: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHighest, label = "stageBg")
    val fg = if (on) cs.onPrimary else cs.onSurfaceVariant
    Surface(shape = CircleShape, color = bg, contentColor = fg) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun ProgressBar(fraction: Float, fill: Color, track: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(fill)
        )
    }
}

// ---------------------------------------------------------------------------------------
// Sound level chart
// ---------------------------------------------------------------------------------------

/** Maps microphone dBFS (-60 .. -10) to 0..1 bar height. */
private fun normLevel(db: Float): Float = ((db + 60f) / 50f).coerceIn(0f, 1f)

private fun beatCodeColor(code: Int, onset: Color, predicted: Color, fallback: Color): Color = when (code) {
    1 -> onset
    2 -> predicted
    else -> fallback
}

@Composable
private fun SoundLevelChart(state: State<Snapshot>, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val bar = cs.primary
    val dim = cs.outlineVariant
    val gate = cs.error
    val fallbackDot = cs.outline
    Canvas(modifier) {
        val s = state.value
        val n = s.levelHistory.size
        val slot = size.width / n
        val barW = slot * 0.64f
        val minH = 2.dp.toPx()
        for (i in 0 until n) {
            val db = s.levelHistory[i]
            val h = max(normLevel(db) * size.height, minH)
            val x = i * slot + (slot - barW) / 2f
            val age = (i + 1f) / n
            val color = if (db >= s.gateDb) bar.copy(alpha = 0.30f + 0.70f * age) else dim
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
            val code = s.beatHistory[i].toInt()
            if (code != 0) {
                drawCircle(
                    color = beatCodeColor(code, BeatAmber, BeatAmber.copy(alpha = 0.6f), fallbackDot),
                    radius = 3.5.dp.toPx(),
                    center = Offset(x + barW / 2f, 6.dp.toPx())
                )
            }
        }
        val gateY = size.height * (1f - normLevel(s.gateDb))
        drawLine(
            color = gate.copy(alpha = 0.75f),
            start = Offset(0f, gateY),
            end = Offset(size.width, gateY),
            strokeWidth = 1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))
        )
    }
}

@Composable
private fun LevelReadout(state: State<Snapshot>, texts: DashTexts) {
    val s = state.value
    val cs = MaterialTheme.colorScheme
    val above = s.running && s.levelDb >= s.gateDb
    Column(horizontalAlignment = Alignment.End) {
        Text(
            if (s.running) String.format(Locale.US, "%d dB", s.levelDb.roundToInt()) else "— dB",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFamily),
            fontWeight = FontWeight.Bold,
            color = if (above) cs.primary else cs.onSurfaceVariant
        )
        if (s.running) {
            Text(
                if (above) texts["dash_gate_above"] else texts["dash_gate_below"],
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Detection chart: onset strength vs adaptive threshold
// ---------------------------------------------------------------------------------------

@Composable
private fun DetectionChart(state: State<Snapshot>, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val fluxColor = cs.primary
    val grid = cs.outlineVariant
    val onsetColor = cs.tertiary
    val predictedColor = BeatAmber
    val fallbackColor = cs.outline
    Canvas(modifier) {
        val s = state.value
        val n = s.fluxHistory.size
        var peak = 0.3f
        for (i in 0 until n) peak = max(peak, max(s.fluxHistory[i], s.thresholdHistory[i]))
        val top = peak * 1.18f
        val dx = size.width / (n - 1)
        fun yOf(v: Float): Float = size.height * (1f - (v / top).coerceIn(0f, 1f))

        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())

        val area = Path().apply {
            moveTo(0f, size.height)
            for (i in 0 until n) lineTo(i * dx, yOf(s.fluxHistory[i]))
            lineTo(size.width, size.height)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(listOf(fluxColor.copy(alpha = 0.40f), fluxColor.copy(alpha = 0.03f)))
        )

        val line = Path().apply {
            moveTo(0f, yOf(s.fluxHistory[0]))
            for (i in 1 until n) lineTo(i * dx, yOf(s.fluxHistory[i]))
        }
        drawPath(
            line,
            fluxColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val thr = Path().apply {
            moveTo(0f, yOf(s.thresholdHistory[0]))
            for (i in 1 until n) lineTo(i * dx, yOf(s.thresholdHistory[i]))
        }
        drawPath(
            thr,
            BeatAmber,
            style = Stroke(width = 1.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f)))
        )

        for (i in 0 until n) {
            val code = s.beatHistory[i].toInt()
            if (code == 0) continue
            val color = beatCodeColor(code, onsetColor, predictedColor, fallbackColor)
            val x = i * dx
            drawLine(color.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            drawCircle(color, 4.dp.toPx(), Offset(x, yOf(s.fluxHistory[i])))
        }
    }
}

@Composable
private fun RatioChip(state: State<Snapshot>, texts: DashTexts) {
    val s = state.value
    val cs = MaterialTheme.colorScheme
    val hot = s.running && s.ratio >= 1f
    val bg by animateColorAsState(if (hot) cs.tertiaryContainer else cs.surfaceContainerHigh, label = "ratioBg")
    Surface(shape = CircleShape, color = bg) {
        Text(
            "${texts["dash_ratio_label"]} " + if (s.running) String.format(Locale.US, "%.1f×", s.ratio) else "—",
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFamily),
            fontWeight = FontWeight.SemiBold,
            color = if (hot) cs.onTertiaryContainer else cs.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------------------
// LED stack
// ---------------------------------------------------------------------------------------

private fun ledNames(texts: DashTexts): List<String> =
    (1..MusicTelemetry.LED_COUNT).map { texts["music_led_name_$it"] }

private fun fallbackLedColor(row: Int): Color {
    val t = row / (MusicTelemetry.LED_COUNT - 1f)
    return if (t < 0.5f) lerp(RampGreen, RampAmber, t * 2f) else lerp(RampAmber, RampRed, (t - 0.5f) * 2f)
}

/** Real LED color if the service published one (and it is visible on screen), else a VU ramp. */
private fun ledColorFor(row: Int, published: IntArray): Color {
    if (published.isNotEmpty()) {
        val index = (row * published.size / MusicTelemetry.LED_COUNT).coerceIn(0, published.size - 1)
        val argb = published[index]
        if (argb != 0) {
            val color = Color(argb).copy(alpha = 1f)
            if (color.luminance() > 0.06f) return color
        }
    }
    return fallbackLedColor(row)
}

/** Instant attack, linear release: the stack jumps on a beat and fades away from the top. */
@Composable
private fun rememberSmoothedLedLevel(state: State<Snapshot>): MutableFloatState {
    val latest = rememberUpdatedState(state)
    val shown = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                val target = latest.value.value.ledLevel
                val current = shown.floatValue
                val next = if (target >= current) target else max(target, current - dt * RELEASE_PER_SECOND)
                if (next != current) shown.floatValue = next
            }
        }
    }
    return shown
}

@Composable
private fun LedStack(state: State<Snapshot>, names: List<String>, compact: Boolean) {
    val cs = MaterialTheme.colorScheme
    val shown = rememberSmoothedLedLevel(state)
    val track = cs.surfaceContainerHighest
    val rowHeight = if (compact) 20.dp else 30.dp
    val labelWidth = if (compact) 92.dp else 116.dp
    val count = MusicTelemetry.LED_COUNT

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp)
    ) {
        for (row in count - 1 downTo 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    names.getOrElse(row) { "LED ${row + 1}" },
                    Modifier.width(labelWidth),
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(rowHeight)
                        .drawBehind {
                            val brightness = (shown.floatValue * count - row).coerceIn(0f, 1f)
                            val corner = CornerRadius(size.height / 2f)
                            drawRoundRect(color = track, cornerRadius = corner)
                            if (brightness > 0.01f) {
                                val color = ledColorFor(row, state.value.ledColors)
                                val pad = 3.dp.toPx()
                                drawRoundRect(
                                    color = color.copy(alpha = 0.22f * brightness),
                                    topLeft = Offset(-pad, -pad / 2f),
                                    size = Size(size.width + pad * 2f, size.height + pad),
                                    cornerRadius = CornerRadius(size.height)
                                )
                                drawRoundRect(
                                    color = color.copy(alpha = 0.18f + 0.82f * brightness),
                                    cornerRadius = corner
                                )
                            }
                        }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Event log
// ---------------------------------------------------------------------------------------

@Composable
private fun EventList(state: State<Snapshot>, texts: DashTexts) {
    val s = state.value
    val cs = MaterialTheme.colorScheme
    if (s.events.isEmpty()) {
        Text(
            texts["dash_events_empty"],
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        s.events.forEach { e ->
            val color = when (e.source) {
                MusicTelemetry.BeatSource.ONSET -> cs.tertiary
                MusicTelemetry.BeatSource.PREDICTED -> BeatAmber
                MusicTelemetry.BeatSource.FALLBACK -> cs.outline
            }
            val name = when (e.source) {
                MusicTelemetry.BeatSource.ONSET -> texts["dash_src_onset"]
                MusicTelemetry.BeatSource.PREDICTED -> texts["dash_src_predicted"]
                MusicTelemetry.BeatSource.FALLBACK -> texts["dash_src_fallback"]
            }
            val ageSeconds = ((s.nowMs - e.atMs) / 1000f).coerceAtLeast(0f)
            val age = if (ageSeconds < 0.4f) texts["dash_event_now"] else String.format(Locale.US, "%.1fs", ageSeconds)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Text(name, Modifier.width(78.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    String.format(
                        Locale.US, "%.1f× · %s %d · %d Hz · %d ms",
                        e.ratio, texts["dash_event_effect"], e.effect, e.frequencyHz, e.deliveryLatencyMs
                    ),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(age, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------------------

private enum class LegendShape { Dot, Dash, Bar }
private data class LegendItem(val color: Color, val label: String, val shape: LegendShape)

@Composable
private fun ChartLegend(items: List<LegendItem>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (item.shape) {
                    LegendShape.Dot -> Box(Modifier.size(8.dp).clip(CircleShape).background(item.color))
                    LegendShape.Dash -> Canvas(Modifier.width(16.dp).height(4.dp)) {
                        drawLine(
                            color = item.color,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))
                        )
                    }
                    LegendShape.Bar -> Box(Modifier.width(3.dp).height(12.dp).clip(CircleShape).background(item.color))
                }
                Text(item.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun IdleHint(state: State<Snapshot>, texts: DashTexts) {
    if (!state.value.running) Hint(texts["dash_idle_hint"])
}

@Composable
private fun DashCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.surfaceContainerLowest,
        border = BorderStroke(1.dp, cs.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title.uppercase(),
                    Modifier.weight(1f),
                    color = cs.primary,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                )
                trailing?.invoke()
            }
            content()
        }
    }
}
