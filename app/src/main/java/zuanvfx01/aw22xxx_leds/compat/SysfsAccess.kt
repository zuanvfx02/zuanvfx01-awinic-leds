package zuanvfx01.aw22xxx_leds.compat

/**
 * Everything the scanner needs from the file system, behind an interface.
 *
 * The real implementation ([AndroidSysfsAccess]) talks to sysfs with errno-aware calls.
 * Unit tests plug in a fake backed by a temp folder, so the scanner can be tested on a
 * plain JVM (no phone, no android.jar) including "permission denied" cases.
 *
 * This file must stay free of android.* imports.
 */
interface SysfsAccess {

    enum class Stat { AVAILABLE, MISSING, RESTRICTED }

    sealed interface Read {
        data class Ok(val text: String) : Read
        data object Missing : Read
        data class Denied(val reason: String) : Read
        data class Failed(val reason: String) : Read
    }

    /** stat() without swallowing EACCES: RESTRICTED means "exists or unknown, but blocked". */
    fun stat(path: String): Stat

    /** errno name for stat(path): "OK", "ENOENT", "EACCES", ... (for logs). */
    fun errno(path: String): String

    fun isDirectory(path: String): Boolean

    fun read(path: String): Read

    /** Child names, or null when the directory cannot be enumerated (missing or denied). */
    fun list(path: String): List<String>?

    /** Final path component of a symlink target (used for device/driver). */
    fun canonicalName(path: String): Read
}

/** Where the scanner looks. Defaults are the real Android paths; tests point them at a temp dir. */
data class ScanPaths(
    val ledClass: String = "/sys/class/leds",
    val sysModule: String = "/sys/module",
    val procModules: String = "/proc/modules",
    val driverDirs: List<String> = listOf("/sys/bus/i2c/drivers", "/sys/bus/platform/drivers"),
) {
    companion object {
        /** Same layout as Android, but rooted somewhere else (e.g. a temp folder in tests). */
        fun under(root: String) = ScanPaths(
            ledClass = "$root/sys/class/leds",
            sysModule = "$root/sys/module",
            procModules = "$root/proc/modules",
            driverDirs = listOf("$root/sys/bus/i2c/drivers", "$root/sys/bus/platform/drivers"),
        )
    }
}

/** Facts about the device/app that the scan result reports but that tests must control. */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val kernel: String,
    val appDomain: String,
    val apkPath: String,
    val systemFlag: Boolean,
)
