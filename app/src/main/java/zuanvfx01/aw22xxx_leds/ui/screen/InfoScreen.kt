package zuanvfx01.aw22xxx_leds.ui.screen

import zuanvfx01.aw22xxx_leds.ui.utils.appString
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.ui.utils.CompatibleDevices
import zuanvfx01.aw22xxx_leds.ui.utils.DeviceBrand
import zuanvfx01.aw22xxx_leds.ui.utils.LedSysfsLogExporter
import zuanvfx01.aw22xxx_leds.ui.utils.LanguagePackManager
import zuanvfx01.aw22xxx_leds.ui.utils.appText
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsGroup
import zuanvfx01.aw22xxx_leds.ui.widgets.SettingsRow
import zuanvfx01.aw22xxx_leds.ui.widgets.TabScaffold

private const val REPO_URL = "https://github.com/zuanvfx02/zuanvfx01-awinic-leds"
private const val LICENSE_URL = "https://github.com/zuanvfx02/zuanvfx01-awinic-leds/blob/main/LICENSE"

@Composable
fun InfoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Bundled/cached list first (instant), then a once-a-day refresh from the repo's JSON.
    var deviceBrands by remember { mutableStateOf<List<DeviceBrand>>(emptyList()) }
    LaunchedEffect(Unit) {
        deviceBrands = withContext(Dispatchers.IO) { CompatibleDevices.load(context) }
        if (withContext(Dispatchers.IO) { CompatibleDevices.refresh(context) }) {
            deviceBrands = withContext(Dispatchers.IO) { CompatibleDevices.load(context) }
        }
    }

    val logCreatedText = appText("log_created", "Log created: %s")
    val logCreateFailedText = appText("log_create_failed", "Failed to create log: %s")

    val exportLanguageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                LanguagePackManager.export(context, uri)
                Toast.makeText(context, LanguagePackManager.get(context, "language_exported", "Language pack exported"), Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, LanguagePackManager.get(context, "language_export_failed", "Language pack export failed: %s").format(error.message ?: "unknown error"), Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLanguageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val count = LanguagePackManager.import(context, uri)
                Toast.makeText(context, LanguagePackManager.get(context, "language_imported", "Language pack imported: %d strings").format(count), Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, LanguagePackManager.get(context, "language_import_failed", "Language pack import failed: %s").format(error.message ?: "unknown error"), Toast.LENGTH_LONG).show()
            }
        }
    }

    val openUrl = { url: String ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    val appVersion = remember(context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    TabScaffold(
        title = appString(R.string.nav_info),
        subtitle = appString(R.string.app_name)
    ) {
        item {
            SettingsGroup(title = appText("app_section", "App")) {
                item {
                    SettingsRow(
                        headline = appString(R.string.app_name),
                        supporting = appText("version_format", "Version %s").format(appVersion),
                        onClick = null,
                        leading = {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }

        item {
            SettingsGroup(title = appText("diagnostics_section", "Diagnostics")) {
                item {
                    SettingsRow(
                        headline = appText("export_led_log", "Export diagnostic"),
                        supporting = appText("export_led_log_description", "Create one log with device, ROM, app domain, APK path and per-file errno to send for support"),
                        leading = {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            scope.launch {
                                // Many sysfs reads: keep them off the main thread.
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { LedSysfsLogExporter.export(context) }
                                }
                                result.onSuccess { file ->
                                    Toast.makeText(context, logCreatedText.format(file.name), Toast.LENGTH_SHORT).show()
                                    LedSysfsLogExporter.shareToTelegram(context, file)
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        logCreateFailedText.format(it.message ?: "unknown error"),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = appText("share", "Share"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }

                item {
                    SettingsRow(
                        headline = appText("led_sysfs_path", "LED sysfs path"),
                        supporting = SysFsBridge.LED_DIR,
                        supportingMono = true,
                        leading = {
                            Icon(
                                imageVector = Icons.Filled.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = null
                    )
                }
            }
        }

        item {
            SettingsGroup(title = appText("language_section", "Language")) {
                item {
                    SettingsRow(
                        headline = appText("export_language_pack", "Export language pack"),
                        supporting = appText("export_language_pack_description", "Create an editable JSON file for translating the app"),
                        onClick = { exportLanguageLauncher.launch("awinic-leds-language.json") }
                    )
                }
                item {
                    SettingsRow(
                        headline = appText("import_language_pack", "Import language pack"),
                        supporting = appText("import_language_pack_description", "Apply a translated JSON language pack to this app"),
                        onClick = { importLanguageLauncher.launch(arrayOf("application/json", "text/plain")) }
                    )
                }
            }
        }

        item {
            SettingsGroup(title = appText("compatible_devices", "Compatible Devices")) {
                deviceBrands.forEach { brand ->
                    item {
                        SettingsRow(
                            headline = brand.name,
                            supporting = brand.models.joinToString("\n"),
                            supportingMaxLines = 6,
                            onClick = null
                        )
                    }
                }
            }
        }

        item {
            SettingsGroup(title = appText("about_section", "About")) {
                item {
                    SettingsRow(
                        headline = appText("source_code", "Source Code"),
                        supporting = appText("source_code_description", "View project on GitHub"),
                        onClick = { openUrl(REPO_URL) }
                    )
                }
                item {
                    SettingsRow(
                        headline = appText("license", "License"),
                        supporting = appText("license_description", "GNU General Public License v3.0"),
                        onClick = { openUrl(LICENSE_URL) }
                    )
                }
            }
        }

        item {
            SettingsGroup(title = appText("developers", "Developers & Contributors")) {
                item {
                    SettingsRow(
                        headline = "ZuanVFX01",
                        supporting = appText("lead_developer", "Lead Developer"),
                        onClick = { openUrl("https://github.com/zuanvfx01") }
                    )
                }
                item {
                    SettingsRow(
                        headline = "ZuanVFX02",
                        supporting = appText("developer_zuanvfx02", "Developer"),
                        onClick = { openUrl("https://github.com/zuanvfx02") }
                    )
                }
            }
        }
    }
}
