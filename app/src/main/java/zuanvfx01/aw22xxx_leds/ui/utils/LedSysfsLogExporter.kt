package zuanvfx01.aw22xxx_leds.ui.utils

import android.content.Context
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge

/**
 * Creates a human-readable snapshot of the AWINIC LED sysfs node.
 *
 * This intentionally reads only the public sysfs files used for diagnostics. No root shell,
 * su, or command execution is required. If the app is installed as a privileged/system app,
 * normal File I/O is used with the permissions already granted by the system.
 */
object LedSysfsLogExporter {

    private val filesInDisplayOrder = listOf(
        "brightness",
        "rgb",
        "reg",
        "imax",
        "max_brightness",
        "task0",
        "task1",
        "frq",
        "trigger",
        "cfg",
        "effect",
        "hwen",
        "fw",
        "uevent",
    )

    fun export(context: Context): File {
        val ledDir = File(SysFsBridge.LED_DIR)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputDir = File(context.cacheDir, "led-diagnostics").apply { mkdirs() }
        val outputFile = File(outputDir, "awinic_led_sysfs_$timestamp.txt")

        outputFile.bufferedWriter().use { out ->
            out.appendLine("AWINIC LED SYSFS DIAGNOSTIC LOG")
            out.appendLine("================================")
            out.appendLine("Generated : ${Date()}")
            out.appendLine()

            // Device information is captured alongside the sysfs snapshot so a log can be
            // diagnosed without needing the tester to provide a second set of screenshots.
            out.appendLine("DEVICE INFORMATION")
            out.appendLine("------------------")
            out.appendLine("ROM       : ${romName()}")
            out.appendLine("Android   : ${androidVersion()}")
            out.appendLine("Device    : ${deviceName()}")
            out.appendLine("Model     : ${Build.MODEL}")
            out.appendLine("Codename  : ${Build.DEVICE}")
            out.appendLine("Kernel    : ${kernelVersion()}")
            out.appendLine("Build ID  : ${Build.ID}")
            out.appendLine("Fingerprint: ${Build.FINGERPRINT}")
            out.appendLine("LED path  : ${ledDir.absolutePath}")
            out.appendLine()

            out.appendLine("SYSFS NODES")
            out.appendLine("===========")
            out.appendLine()

            filesInDisplayOrder.forEachIndexed { index, name ->
                val file = File(ledDir, name)
                out.appendLine("${index + 1}. ${name.uppercase(Locale.US)}")
                out.appendLine("Path: ${file.absolutePath}")
                out.appendLine("--------------------------------")

                if (!file.exists()) {
                    out.appendLine("<NOT FOUND>")
                } else {
                    val result = runCatching {
                        file.readText(Charsets.UTF_8).trimEnd()
                    }

                    result.onSuccess { content ->
                        out.appendLine(if (content.isEmpty()) "<EMPTY>" else content)
                    }.onFailure { error ->
                        out.appendLine("<READ FAILED: ${error.javaClass.simpleName}: ${error.message ?: "unknown error"}>")
                    }
                }

                out.appendLine()
                out.appendLine()
            }

            out.appendLine("END OF LOG")
        }

        return outputFile
    }

    private fun deviceName(): String = runCatching {
        listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { Build.DEVICE.ifBlank { "Unknown" } }
    }.getOrDefault("Unknown")

    private fun androidVersion(): String =
        "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    /**
     * Build.DISPLAY is the most useful generic ROM/build label available without root.
     * On custom ROMs it commonly contains the ROM name/version; on stock builds it falls
     * back to the vendor build identifier.
     */
    private fun romName(): String = runCatching {
        Build.DISPLAY.takeIf { it.isNotBlank() }
            ?: Build.VERSION.INCREMENTAL.takeIf { it.isNotBlank() }
            ?: "Unknown"
    }.getOrDefault("Unknown")

    private fun kernelVersion(): String = runCatching {
        System.getProperty("os.version")?.takeIf { it.isNotBlank() } ?: "Unknown"
    }.getOrDefault("Unknown")

    fun shareToTelegram(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                "AWINIC LED diagnostic log\nhttps://t.me/zuanvfx01"
            )
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("org.telegram.messenger")
        }

        runCatching {
            context.startActivity(intent)
        }.onFailure {
            val fallback = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(
                    android.content.Intent.EXTRA_TEXT,
                    "AWINIC LED diagnostic log\nhttps://t.me/zuanvfx01"
                )
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                android.content.Intent.createChooser(fallback, "Share AWINIC LED log")
            )
        }
    }
}
