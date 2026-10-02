package zuanvfx01.aw22xxx_leds.ui.utils

import android.content.Context
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.compat.AndroidSysfsAccess
import zuanvfx01.aw22xxx_leds.compat.CompatibilityScanner

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

    private const val MAX_READ_BYTES = 64 * 1024

    /**
     * Blocking: touches many sysfs files. Call from a background dispatcher.
     */
    fun export(context: Context): File {
        val scan = runCatching { CompatibilityScanner.scan() }
        val ledDir = File(scan.getOrNull()?.selectedNode?.let { "/sys/class/leds/$it" } ?: SysFsBridge.LED_DIR)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputDir = File(context.cacheDir, "led-diagnostics").apply { mkdirs() }
        val outputFile = File(outputDir, "awinic_led_diagnostic_$timestamp.txt")

        outputFile.bufferedWriter().use { out ->
            out.appendLine("AWINIC LED DIAGNOSTIC LOG")
            out.appendLine("=========================")
            out.appendLine("Generated : ${Date()}")
            out.appendLine()

            // ---- device -----------------------------------------------------------------
            out.appendLine("DEVICE INFORMATION")
            out.appendLine("------------------")
            out.appendLine("Device    : ${deviceName()}")
            out.appendLine("Brand     : ${Build.BRAND}")
            out.appendLine("Model     : ${Build.MODEL}")
            out.appendLine("Codename  : ${Build.DEVICE}")
            out.appendLine("Product   : ${Build.PRODUCT}")
            out.appendLine("Hardware  : ${Build.HARDWARE} / board ${Build.BOARD}")
            out.appendLine("ROM       : ${romName()}")
            out.appendLine("Android   : ${androidVersion()}")
            out.appendLine("Sec. patch: ${Build.VERSION.SECURITY_PATCH}")
            out.appendLine("Kernel    : ${kernelVersion()}")
            out.appendLine("Build ID  : ${Build.ID}")
            out.appendLine("Fingerprint: ${Build.FINGERPRINT}")
            out.appendLine("SELinux   : ${selinuxMode()}")
            out.appendLine()

            // ---- app environment --------------------------------------------------------
            out.appendLine("APP ENVIRONMENT")
            out.appendLine("---------------")
            val pkgInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            out.appendLine("Package   : ${context.packageName}")
            out.appendLine("Version   : ${pkgInfo?.versionName ?: "?"} (code ${pkgInfo?.longVersionCode ?: "?"})")
            out.appendLine("UID       : ${runCatching { Os.getuid() }.getOrDefault(-1)}")
            out.appendLine("App domain: ${AndroidSysfsAccess.readSelfDomain().ifBlank { "unknown" }}")
            val apk = AndroidSysfsAccess.readApkPath()
            out.appendLine("APK path  : ${apk.ifBlank { "unknown" }}")
            val sysFlag = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            out.appendLine("System app: $sysFlag")
            out.appendLine("LED path  : ${ledDir.absolutePath}")
            out.appendLine()

            // ---- scan result ------------------------------------------------------------
            out.appendLine("COMPATIBILITY SCAN")
            out.appendLine("------------------")
            scan.onSuccess { r ->
                out.appendLine("Compatible : ${r.compatible} (partial: ${r.partial})")
                out.appendLine("Failure    : ${r.failure}")
                out.appendLine("Driver     : found=${r.driverFound} state=${r.driverState}")
                out.appendLine("Access OK  : ${r.accessOk}")
                out.appendLine("Node       : ${r.selectedNode ?: "<none>"}")
                out.appendLine("LED nodes  : ${r.ledNodes.joinToString().ifBlank { "<none visible>" }}")
                out.appendLine("Drivers    : ${r.driverNames.joinToString().ifBlank { "<none>" }}")
                out.appendLine("Capabilities:")
                r.capabilities.values.forEach { c ->
                    out.appendLine("  - ${c.name.padEnd(15)} ${c.state}${c.reason?.let { " ($it)" } ?: ""}")
                }
                out.appendLine("Details:")
                r.details.forEach { out.appendLine("  * $it") }
            }.onFailure {
                out.appendLine("<SCAN FAILED: ${it.javaClass.simpleName}: ${it.message}>")
            }
            out.appendLine()

            // ---- directory listing ------------------------------------------------------
            out.appendLine("LED CLASS DIRECTORY (/sys/class/leds)")
            out.appendLine("-------------------------------------")
            out.appendLine("stat      : ${AndroidSysfsAccess.errno("/sys/class/leds")}")
            val listing = runCatching { File("/sys/class/leds").list() }.getOrNull()
            if (listing == null) out.appendLine("<cannot enumerate>")
            else listing.sorted().forEach { out.appendLine("  $it") }
            out.appendLine()

            out.appendLine("SYSFS NODES (${ledDir.absolutePath})")
            out.appendLine("=====================================")
            out.appendLine("node stat : ${AndroidSysfsAccess.errno(ledDir.absolutePath)}")
            out.appendLine()

            filesInDisplayOrder.forEachIndexed { index, name ->
                val file = File(ledDir, name)
                val path = file.absolutePath
                out.appendLine("${index + 1}. ${name.uppercase(Locale.US)}")
                out.appendLine("Path   : $path")
                out.appendLine("stat   : ${AndroidSysfsAccess.errno(path)}")
                out.appendLine("access : R=${AndroidSysfsAccess.accessErrno(path, OsConstants.R_OK)} " +
                    "W=${AndroidSysfsAccess.accessErrno(path, OsConstants.W_OK)}")
                out.appendLine("owner  : ${fileOwnerInfo(path)}")
                out.appendLine("context: ${selinuxContext(path)}")
                out.appendLine("--------------------------------")

                val (content, readErrno) = readRaw(path)
                if (content == null) {
                    out.appendLine("<READ FAILED: $readErrno>")
                } else {
                    val text = content.trimEnd()
                    out.appendLine(if (text.isEmpty()) "<EMPTY>" else text)
                }

                out.appendLine()
                out.appendLine()
            }

            out.appendLine("END OF LOG")
        }

        return outputFile
    }

    /** open+read with errno kept, so "EACCES" and "ENOENT" are never mixed up. */
    private fun readRaw(path: String): Pair<String?, String> = try {
        val fd = Os.open(path, OsConstants.O_RDONLY, 0)
        try {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (out.size() < MAX_READ_BYTES) {
                val n = Os.read(fd, buf, 0, buf.size)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
            out.toString("UTF-8") to "OK"
        } finally {
            runCatching { Os.close(fd) }
        }
    } catch (e: ErrnoException) {
        null to (OsConstants.errnoName(e.errno) ?: "errno=${e.errno}")
    } catch (e: Exception) {
        null to "${e.javaClass.simpleName}: ${e.message}"
    }

    private fun fileOwnerInfo(path: String): String = try {
        val st = Os.stat(path)
        "mode=%04o uid=%d gid=%d".format(st.st_mode and 0xFFF, st.st_uid, st.st_gid)
    } catch (e: ErrnoException) {
        OsConstants.errnoName(e.errno) ?: "errno=${e.errno}"
    } catch (e: Exception) {
        e.javaClass.simpleName
    }

    /** security.selinux xattr. Os.getxattr is not public API on every level, so use reflection. */
    private fun selinuxContext(path: String): String = runCatching {
        val m = Os::class.java.getMethod("getxattr", String::class.java, String::class.java)
        val bytes = m.invoke(null, path, "security.selinux") as ByteArray
        String(bytes, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
    }.getOrElse { "<unavailable>" }

    private fun selinuxMode(): String {
        val (text, err) = readRaw("/sys/fs/selinux/enforce")
        val value = text?.trim()
        return when (value) {
            "1" -> "Enforcing"
            "0" -> "Permissive"
            null -> "unknown ($err)"
            else -> value
        }
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
