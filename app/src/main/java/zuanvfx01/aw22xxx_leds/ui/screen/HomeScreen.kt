package zuanvfx01.aw22xxx_leds.ui.screen

import zuanvfx01.aw22xxx_leds.ui.utils.appString
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.bridge.LedIMax
import zuanvfx01.aw22xxx_leds.bridge.LedReg
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.ui.model.LedsUiState
import zuanvfx01.aw22xxx_leds.ui.model.LedsViewModel
import zuanvfx01.aw22xxx_leds.ui.theme.MonoFamily
import zuanvfx01.aw22xxx_leds.ui.widgets.ColorSwatch
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsGroup
import zuanvfx01.aw22xxx_leds.ui.widgets.TabScaffold
import zuanvfx01.aw22xxx_leds.ui.widgets.ValueRow
import zuanvfx01.aw22xxx_leds.ui.widgets.toHex

/**
 * "IMax20mA" -> "20 mA". Returns null for [LedIMax.Values.Unknown].
 */
private fun LedIMax.Values.label(): String? =
    if (this == LedIMax.Values.Unknown) null
    else name.removePrefix("IMax").replace("mA", " mA")

/**
 * Home tab: read-only, live (1 s) overview of the controller. Shares the [LedsViewModel] with
 * Settings so there is a single polling loop. Registers and the trigger are read directly
 * from the bridge on each state tick; failures fall back to empty values instead of crashing.
 */
@Composable
fun HomeScreen(viewModel: LedsViewModel) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    val present = remember(uiState) { SysFsBridge.isPresent }
    val registers = remember(uiState) {
        runCatching { SysFsBridge.IO.registers }.getOrDefault(emptyList())
    }
    val trigger = remember(uiState) {
        runCatching { SysFsBridge.IO.triggers }
            .getOrNull()
            ?.let { (list, selected) -> list.getOrNull(selected)?.trim() }
            ?.takeIf { it.isNotEmpty() }
    }

    val effectName = uiState.availableEffect
        .firstOrNull { it.index.toInt() == uiState.currentEffect }
        ?.name
        ?: uiState.availableEffect.getOrNull(uiState.currentEffect)?.name

    val dash = appString(R.string.value_none)

    TabScaffold(
        title = appString(R.string.nav_home),
        subtitle = appString(R.string.home_subtitle)
    ) {
        item { HeroCard(uiState, present, effectName) }

        item {
            SettingsGroup(title = appString(R.string.home_section_controller)) {
                item {
                    ValueRow(
                        appString(R.string.home_status),
                        appString(
                            if (present) R.string.home_status_online
                            else R.string.home_status_offline
                        )
                    )
                }
                item {
                    ValueRow(
                        appString(R.string.home_sysfs_path),
                        SysFsBridge.LED_DIR,
                        valueMono = true
                    )
                }
                if (SysFsBridge.supports("imax") || !SysFsBridge.isPresent) {
                    item {
                        ValueRow(
                            appString(R.string.home_imax),
                            uiState.currentIMax.label() ?: appString(R.string.value_unknown)
                        )
                    }
                }
                if (SysFsBridge.supports("trigger") || !SysFsBridge.isPresent) {
                    item {
                        ValueRow(appString(R.string.home_trigger), trigger ?: dash, valueMono = true)
                    }
                }
            }
        }

        item {
            SettingsGroup(title = appString(R.string.home_section_state)) {
                item {
                    ValueRow(
                        appString(R.string.led_hwen_title),
                        appString(
                            if (uiState.enabled) R.string.home_leds_on else R.string.home_leds_off
                        )
                    )
                }
                item {
                    ValueRow(appString(R.string.home_effect), effectName ?: dash)
                }
                if (SysFsBridge.supports("frq") || !SysFsBridge.isPresent) {
                    item {
                        ValueRow(
                            appString(R.string.home_frequency),
                            appString(R.string.value_hz, uiState.frequency)
                        )
                    }
                }
            }
        }

        item {
            SettingsGroup(
                title = pluralStringResource(
                    R.plurals.home_colors_count,
                    uiState.colors.size,
                    uiState.colors.size
                )
            ) {
                uiState.colors.forEachIndexed { index, color ->
                    item {
                        ValueRow(
                            appString(R.string.led_rgb_title, index),
                            color.toHex(),
                            valueMono = true,
                            leading = { ColorSwatch(color, size = 24) }
                        )
                    }
                }
            }
        }

        if (SysFsBridge.supports("reg") && registers.isNotEmpty()) {
            item {
                SettingsGroup(
                    title = pluralStringResource(
                        R.plurals.home_registers_count,
                        registers.size,
                        registers.size
                    )
                ) {
                    item { RegisterDump(registers) }
                }
            }
        }
    }
}

/**
 * The one "loud" element on the page: a large primary-container card with the effect name
 * as the headline and the live LED colors as a row of swatches.
 */
@Composable
private fun HeroCard(uiState: LedsUiState, present: Boolean, effectName: String?) {
    val colorScheme = MaterialTheme.colorScheme
    val active = present && uiState.enabled

    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (active) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
        contentColor = if (active) colorScheme.onPrimaryContainer else colorScheme.onSurface
    ) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(
                    when {
                        !present -> colorScheme.error
                        uiState.enabled -> colorScheme.primary
                        else -> colorScheme.outline
                    }
                )
                Text(
                    appString(
                        when {
                            !present -> R.string.home_status_offline
                            uiState.enabled -> R.string.home_leds_on
                            else -> R.string.home_leds_off
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(
                effectName ?: appString(R.string.value_none),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                uiState.colors.forEach { ColorSwatch(it, size = 40) }
            }

            Text(
                SysFsBridge.LED_DIR,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                color = LocalContentColorMuted()
            )
        }
    }
}

@Composable
private fun LocalContentColorMuted(): Color =
    MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun StatusDot(color: Color) {
    Surface(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
        color = color,
        shape = CircleShape
    ) {}
}

/**
 * Compact monospace dump: four "II:VV" pairs per line, hex, zero padded.
 */
@Composable
private fun RegisterDump(registers: List<LedReg>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        registers.chunked(4).forEach { line ->
            Text(
                line.joinToString("   ") {
                    "%02X:%02X".format(it.index.toInt(), it.value.toInt())
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
