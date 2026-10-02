package zuanvfx01.aw22xxx_leds.ui.widgets

import zuanvfx01.aw22xxx_leds.ui.utils.appString
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import zuanvfx01.aw22xxx_leds.ui.glass.AppDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import zuanvfx01.aw22xxx_leds.R
import kotlin.math.absoluteValue

@Composable
private fun measureTextSize(style: TextStyle): Pair<Dp, Dp> {
    val textMeasurer = rememberTextMeasurer()
    val px = textMeasurer.measure("0000000000", style).size

    return with(LocalDensity.current) { px.width.toDp() to px.height.toDp() }
}

@Composable
fun <T> TitledVerticalPagerDialog(
    currentValueIndex: Int,
    onValueChange: (Int) -> Unit,

    values: List<T>,
    valueKey: (T) -> String,

    title: String,
    description: String?,

    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .clickable { expanded = true },
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)

            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Gray
                )
            }
        }

        Text(valueKey(values[currentValueIndex]))
    }

    if (expanded) {
        val pagerState = rememberPagerState(currentValueIndex) { values.size }

        val textStyle = MaterialTheme.typography.displaySmall
        val textSize = measureTextSize(textStyle)

        // Shared by the glass dialog (Android 12+) and the old Dialog + Card (Android 11-).
        val pager: @Composable () -> Unit = {
            VerticalPager(
                pagerState,
                Modifier
                    .width(textSize.first)
                    .height(textSize.second * 5),
                pageSize = PageSize.Fixed(textSize.second),
                beyondViewportPageCount = 2,
                pageSpacing = textSize.second / 2,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { page ->
                Text(
                    valueKey(values[page]),
                    Modifier
                        .offset(0.dp, textSize.second * 2f)
                        .graphicsLayer {
                        val offset = pagerState.getOffsetDistanceInPages(
                            page.coerceIn(0, pagerState.pageCount - 1)
                        ).absoluteValue

                        lerp(
                            start = 1f, stop = 0.95f, fraction = offset.coerceIn(0f, 1f)
                        ).also { scale ->
                            scaleX = scale
                            scaleY = scale
                        }
                        alpha = lerp(
                            start = 0.5f, stop = 1f, fraction = 1f - offset.coerceIn(0f, 1f)
                        )
                    },
                    style = textStyle
                )
            }
        }

        AppDialog(
            onDismissRequest = { expanded = false },
            title = title,
            confirmText = appString(R.string.ok),
            onConfirm = {
                onValueChange(pagerState.currentPage)
                expanded = false
            },
            dismissText = appString(R.string.cancel),
            onDismiss = { expanded = false },
            legacyDialog = {
                // Android 11-: unchanged from before.
                Dialog({ expanded = true }) {
                    Card {
                        Column(horizontalAlignment = Alignment.End) {
                            pager()

                            TextButton({
                                onValueChange(pagerState.currentPage)
                                expanded = false
                            }) {
                                Text(appString(R.string.ok))
                            }
                        }
                    }
                }
            },
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { pager() }
        }
    }
}
