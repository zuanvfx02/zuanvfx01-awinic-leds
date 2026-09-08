package zuanvfx01.aw22xxx_leds.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/zuanvfx02/zuanvfx01-awinic-leds/main/update.json"
private const val PREFS = "update_checker"
private const val KEY_DISMISSED = "dismissed_version"

data class ModuleUpdate(
    val version: String,
    val versionCode: Int,
    val zipUrl: String,
    val changelogUrl: String
)

object GitHubUpdateChecker {
    suspend fun latest(): ModuleUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(UPDATE_JSON_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Awinic-Leds")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val version = json.optString("version").trim()
                val zipUrl = json.optString("zipUrl").trim()
                if (version.isBlank() || zipUrl.isBlank()) return@runCatching null
                ModuleUpdate(
                    version = version.removePrefix("v").trim(),
                    versionCode = json.optInt("versionCode", 0),
                    zipUrl = zipUrl,
                    changelogUrl = json.optString("changelog").trim()
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    suspend fun downloadModule(context: Context, update: ModuleUpdate): File? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(update.zipUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Awinic-Leds")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val target = File(context.cacheDir, "zuanvfx01-awinic-leds-module-${update.version}.zip")
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (!target.isFile || target.length() == 0L) null else target
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    fun openModuleZip(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun isNewer(current: String, remote: String): Boolean {
        val a = parseVersion(current)
        val b = parseVersion(remote)
        for (i in 0..2) {
            if (a[i] != b[i]) return b[i] > a[i]
        }
        return false
    }

    fun shouldShow(context: Context, version: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DISMISSED, null) != version

    fun dismiss(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED, version)
            .apply()
    }

    private fun parseVersion(value: String): List<Int> = value
        .removePrefix("v")
        .trim()
        .split(".")
        .take(3)
        .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        .let { it + List(3 - it.size) { 0 } }
}

@Composable
fun GitHubUpdateDialog() {
    val context = LocalContext.current
    var update by remember { mutableStateOf<ModuleUpdate?>(null) }
    var visible by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    val currentVersion = remember { zuanvfx01.aw22xxx_leds.BuildConfig.VERSION_NAME }

    LaunchedEffect(Unit) {
        val latest = GitHubUpdateChecker.latest()
        if (latest != null && GitHubUpdateChecker.isNewer(currentVersion, latest.version) && GitHubUpdateChecker.shouldShow(context, latest.version)) {
            update = latest
            visible = true
        }
    }

    val available = update
    if (visible && available != null) {
        AlertDialog(
            onDismissRequest = {
                GitHubUpdateChecker.dismiss(context, available.version)
                visible = false
            },
            title = { Text(appText("update_available_title", "Update available")) },
            text = {
                Text(
                    if (downloading) {
                        appText("update_downloading", "Downloading module %s...").format(available.version)
                    } else {
                        appText("update_available_message", "A newer module version %s is available. Your version is %s. Download the module ZIP and flash it manually in Magisk.")
                            .format(available.version, currentVersion)
                    }
                )
            },
            confirmButton = {
                Button(
                    enabled = !downloading,
                    onClick = {
                        downloading = true
                    }
                ) {
                    Text(
                        if (downloading) {
                            appText("update_downloading_button", "Downloading...")
                        } else {
                            appText("update_now", "Download module")
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !downloading,
                    onClick = {
                        GitHubUpdateChecker.dismiss(context, available.version)
                        visible = false
                    }
                ) {
                    Text(appText("update_later", "Later"))
                }
            }
        )
    }

    if (downloading && available != null) {
        LaunchedEffect(available.version) {
            val file = GitHubUpdateChecker.downloadModule(context, available)
            downloading = false
            if (file != null) {
                GitHubUpdateChecker.dismiss(context, available.version)
                visible = false
                runCatching { GitHubUpdateChecker.openModuleZip(context, file) }
            }
        }
    }
}
