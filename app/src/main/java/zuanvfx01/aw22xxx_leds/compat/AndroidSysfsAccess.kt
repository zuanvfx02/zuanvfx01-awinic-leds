package zuanvfx01.aw22xxx_leds.compat

import android.content.pm.ApplicationInfo
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import zuanvfx01.aw22xxx_leds.Application

/** Real sysfs access. Keeps errno so "blocked by SELinux" is never confused with "missing". */
object AndroidSysfsAccess : SysfsAccess {

    override fun stat(path: String): SysfsAccess.Stat = try {
        Os.stat(path)
        SysfsAccess.Stat.AVAILABLE
    } catch (e: ErrnoException) {
        when (e.errno) {
            OsConstants.ENOENT, OsConstants.ENOTDIR -> SysfsAccess.Stat.MISSING
            OsConstants.EACCES, OsConstants.EPERM -> SysfsAccess.Stat.RESTRICTED
            else -> SysfsAccess.Stat.MISSING
        }
    } catch (_: SecurityException) {
        SysfsAccess.Stat.RESTRICTED
    }

    override fun errno(path: String): String = try {
        Os.stat(path)
        "OK"
    } catch (e: ErrnoException) {
        OsConstants.errnoName(e.errno) ?: "errno=${e.errno}"
    } catch (e: Exception) {
        e.javaClass.simpleName
    }

    override fun isDirectory(path: String): Boolean = try {
        File(path).isDirectory
    } catch (_: SecurityException) {
        false
    }

    override fun read(path: String): SysfsAccess.Read {
        when (stat(path)) {
            SysfsAccess.Stat.MISSING -> return SysfsAccess.Read.Missing
            SysfsAccess.Stat.RESTRICTED -> return SysfsAccess.Read.Denied("Permission denied")
            SysfsAccess.Stat.AVAILABLE -> Unit
        }
        return try {
            SysfsAccess.Read.Ok(File(path).readText())
        } catch (e: SecurityException) {
            SysfsAccess.Read.Denied(e.message ?: "Permission denied")
        } catch (e: Exception) {
            if (isPermissionError(e)) SysfsAccess.Read.Denied(e.message ?: "Permission denied")
            else SysfsAccess.Read.Failed(e.message ?: "Read failed")
        }
    }

    override fun list(path: String): List<String>? =
        runCatching { File(path).listFiles() }.getOrNull()?.map { it.name }

    override fun canonicalName(path: String): SysfsAccess.Read = try {
        when (stat(path)) {
            SysfsAccess.Stat.AVAILABLE -> SysfsAccess.Read.Ok(File(path).canonicalFile.name)
            SysfsAccess.Stat.MISSING -> SysfsAccess.Read.Missing
            SysfsAccess.Stat.RESTRICTED -> SysfsAccess.Read.Denied("Permission denied")
        }
    } catch (e: SecurityException) {
        SysfsAccess.Read.Denied(e.message ?: "Permission denied")
    } catch (e: Exception) {
        if (isPermissionError(e)) SysfsAccess.Read.Denied(e.message ?: "Permission denied")
        else SysfsAccess.Read.Failed(e.message ?: "Unable to resolve driver")
    }

    /** errno name of access(path, mode), or "OK". Used by the diagnostic export. */
    fun accessErrno(path: String, mode: Int): String = try {
        if (Os.access(path, mode)) "OK" else "DENIED"
    } catch (e: ErrnoException) {
        OsConstants.errnoName(e.errno) ?: "errno=${e.errno}"
    } catch (e: Exception) {
        e.javaClass.simpleName
    }

    private fun isPermissionError(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is ErrnoException && current.errno == OsConstants.EACCES) return true
            val message = current.message.orEmpty()
            if (message.contains("EACCES", true) || message.contains("permission denied", true) ||
                message.contains("operation not permitted", true)) return true
            current = current.cause
        }
        return false
    }

    // ---- device facts -------------------------------------------------------------------

    /** SELinux domain of this very process, e.g. u:r:priv_app:s0:c512,c768 (readable by self). */
    fun readSelfDomain(): String = runCatching {
        File("/proc/self/attr/current").readText().trim { it <= ' ' || it == '\u0000' }
    }.getOrDefault("")

    fun readApkPath(): String = runCatching {
        Application.INSTANCE.applicationInfo.sourceDir.orEmpty()
    }.getOrDefault("")

    fun currentDeviceInfo(): DeviceInfo {
        val flags = runCatching { Application.INSTANCE.applicationInfo.flags }.getOrDefault(0)
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            kernel = runCatching { System.getProperty("os.version") ?: "unknown" }.getOrDefault("unknown"),
            appDomain = readSelfDomain(),
            apkPath = readApkPath(),
            systemFlag = flags and ApplicationInfo.FLAG_SYSTEM != 0,
        )
    }
}
