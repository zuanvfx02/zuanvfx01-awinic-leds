package zuanvfx01.aw22xxx_leds.ui.screen

import zuanvfx01.aw22xxx_leds.ui.utils.appText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import zuanvfx01.aw22xxx_leds.compat.CompatibilityScanner

@Composable
fun ResultContent(
    result: CompatibilityScanner.Result?,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    val compatible = result?.compatible == true
    val hasDriver = result?.driverFound == true
    val containerColor by animateColorAsState(
        when {
            compatible -> MaterialTheme.colorScheme.primaryContainer
            hasDriver -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.errorContainer
        }, label = "status-color"
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(appText("compatibility_report", "AWINIC LED COMPATIBILITY REPORT"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("================================", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = containerColor)
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when {
                                compatible -> Icons.Filled.CheckCircle
                                hasDriver -> Icons.Filled.WarningAmber
                                else -> Icons.Filled.ErrorOutline
                            },
                            contentDescription = null,
                            Modifier.size(44.dp)
                        )
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(
                                when {
                                    compatible -> if (result?.partial == true) "Compatible — partial capability set" else "Compatible — full capability set"
                                    hasDriver -> "AWINIC driver found — interface incomplete"
                                    else -> "AWINIC driver not found"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when {
                                    compatible -> "The driver exists and can be used. Unavailable optional features will be disabled automatically."
                                    hasDriver -> "Driver evidence exists, but the minimum functional interface is not available."
                                    else -> "No usable AWINIC/AW22XXX driver evidence was found."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportLine("Driver", when (result?.driverState) {
                        CompatibilityScanner.CapabilityState.AVAILABLE -> "FOUND"
                        CompatibilityScanner.CapabilityState.RESTRICTED -> "FOUND (restricted evidence)"
                        else -> "NOT FOUND"
                    })
                    ReportLine("Sysfs root", result?.selectedNode?.let { "/sys/class/leds/$it/" } ?: "/sys/class/leds/")
                    ReportLine("Core", "${result?.coreAvailable ?: 0}/${CompatibilityScanner.coreCapabilities.size} available")
                    ReportLine("Optional", "${result?.optionalAvailable ?: 0}/${CompatibilityScanner.optionalCapabilities.size} available")
                    ReportLine("Restricted", "${result?.restrictedCount ?: 0}")
                }
            }
        }

        item { CapabilitySection("CORE CAPABILITIES", result?.core().orEmpty()) }
        items(result?.core().orEmpty(), key = { "core-${it.name}" }) { CapabilityRow(it) }

        item { CapabilitySection("OPTIONAL CAPABILITIES", result?.optional().orEmpty()) }
        items(result?.optional().orEmpty(), key = { "optional-${it.name}" }) { CapabilityRow(it) }

        val restricted = result?.restricted().orEmpty().filter {
            it.state == CompatibilityScanner.CapabilityState.RESTRICTED
        }
        if (restricted.isNotEmpty()) {
            item { CapabilitySection("RESTRICTED", restricted) }
            items(restricted, key = { "restricted-${it.name}" }) { CapabilityRow(it) }
        }

        if (!result?.driverNames.isNullOrEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(appText("driver_evidence", "Driver evidence"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        result!!.driverNames.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(appText("device", "Device"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    InfoLine("Manufacturer", result?.manufacturer ?: "—")
                    InfoLine("Model", result?.model ?: "—")
                    InfoLine("Android", result?.androidVersion ?: "—")
                    InfoLine("Kernel", result?.kernel ?: "—")
                }
            }
        }

        item {
            Text(appText("result", "RESULT"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("================================", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    compatible -> "Compatible — partial capability set\nThe driver exists and can be used.\nUnavailable optional features will be disabled automatically."
                    hasDriver -> "Driver found, but the minimum functional capability set is incomplete."
                    else -> "Incompatible — no AWINIC driver evidence."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(appText("scan_again", "Scan again"))
                }
                if (compatible) {
                    Button(onClick = onContinue, modifier = Modifier.weight(1f)) { Text(appText("open_menu", "Open menu")) }
                }
            }
        }
    }
}

@Composable
private fun CapabilitySection(title: String, capabilities: List<CompatibilityScanner.Capability>) {
    if (capabilities.isEmpty()) return
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("--------------------------------", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CapabilityRow(capability: CompatibilityScanner.Capability) {
    val state = capability.state
    val (icon, label, tint) = when (state) {
        CompatibilityScanner.CapabilityState.AVAILABLE -> Triple(Icons.Filled.CheckCircle, "AVAILABLE", MaterialTheme.colorScheme.primary)
        CompatibilityScanner.CapabilityState.RESTRICTED -> Triple(Icons.Filled.Security, "RESTRICTED", MaterialTheme.colorScheme.tertiary)
        CompatibilityScanner.CapabilityState.UNAVAILABLE -> Triple(Icons.Filled.ErrorOutline, "UNAVAILABLE", MaterialTheme.colorScheme.error)
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, Modifier.size(23.dp), tint = tint)
            Text(capability.name, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
            Text(label, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(.65f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.65f), fontWeight = FontWeight.Medium)
    }
}
