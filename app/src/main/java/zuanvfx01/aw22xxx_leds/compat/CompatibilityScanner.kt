package zuanvfx01.aw22xxx_leds.compat

import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Capability-based, read-only AWINIC/AW22XXX hardware probe.
 *
 * Important: this scanner never treats a permission error as "driver missing".
 * A capability can be AVAILABLE, UNAVAILABLE, or RESTRICTED. Compatibility is
 * decided from the driver evidence plus the minimum functional LED interface,
 * not from a fixed number of sysfs files.
 */
object CompatibilityScanner {
    private const val LED_CLASS = "/sys/class/leds"

    private val driverPattern = Regex("(?i)(aw22xxx|aw210xx|awinic)")
    private val knownNodes = listOf("aw22xxx_led", "aw210xx_led", "aw22xxx", "aw210xx")

    enum class CapabilityState { AVAILABLE, UNAVAILABLE, RESTRICTED }

    data class Capability(
        val name: String,
        val state: CapabilityState,
        val path: String? = null,
        val reason: String? = null,
    )

    // These are the capabilities the app is fundamentally built around.
    // A device needs the basic control surface (brightness + rgb + cfg + effect)
    // to be considered usable. max_brightness and hwen remain CORE capabilities
    // and are reported separately, but their absence does not reject the device.
    val coreCapabilities = listOf(
        "brightness", "rgb", "max_brightness", "cfg", "effect", "hwen"
    )

    val optionalCapabilities = listOf(
        "reg", "imax", "task0", "task1", "frq", "trigger"
    )

    val restrictedProbeNames = listOf("fw", "uevent")
    private val allCapabilities = coreCapabilities + optionalCapabilities + restrictedProbeNames

    data class Result(
        val compatible: Boolean,
        val partial: Boolean,
        val driverFound: Boolean,
        val driverState: CapabilityState,
        val accessOk: Boolean,
        val ledNodes: List<String>,
        val driverNames: List<String>,
        val matchedNodes: List<String>,
        val selectedNode: String?,
        val capabilities: Map<String, Capability>,
        val details: List<String>,
        // Kept for source compatibility with older callers/cache code.
        val interfaceStatus: Map<String, Boolean>,
        val manufacturer: String = Build.MANUFACTURER,
        val model: String = Build.MODEL,
        val androidVersion: String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        val kernel: String = runCatching { System.getProperty("os.version") ?: "unknown" }.getOrDefault("unknown"),
    ) {
        fun capability(name: String): Capability = capabilities[name]
            ?: Capability(name, CapabilityState.UNAVAILABLE)

        fun state(name: String): CapabilityState = capability(name).state

        fun available(name: String): Boolean = state(name) == CapabilityState.AVAILABLE

        fun core(): List<Capability> = coreCapabilities.map(::capability)
        fun optional(): List<Capability> = optionalCapabilities.map(::capability)
        fun restricted(): List<Capability> = restrictedProbeNames.map(::capability)

        val coreAvailable: Int get() = core().count { it.state == CapabilityState.AVAILABLE }
        val optionalAvailable: Int get() = optional().count { it.state == CapabilityState.AVAILABLE }
        val restrictedCount: Int get() = restricted().count { it.state == CapabilityState.RESTRICTED }
    }

    fun deviceFingerprint(): String = listOf(
        Build.MANUFACTURER, Build.MODEL, Build.FINGERPRINT, Build.VERSION.SDK_INT
    ).joinToString("|")

    fun scan(): Result {
        val root = File(LED_CLASS)
        val details = mutableListOf<String>()
        val nodes = linkedSetOf<String>()
        val drivers = linkedSetOf<String>()
        val matched = linkedSetOf<String>()
        val restrictedEvidence = linkedSetOf<String>()
        var enumerationAvailable = false

        val listed = runCatching { root.listFiles() }.getOrNull()
        if (listed != null) {
            enumerationAvailable = true
            listed.filter { it.isDirectory }.forEach { nodes += it.name }
        } else {
            details += "Enumerasi $LED_CLASS dibatasi; direct probe tetap digunakan."
        }

        fun addDriverEvidence(value: String?, source: String) {
            if (value.isNullOrBlank()) return
            if (driverPattern.containsMatchIn(value)) {
                drivers += value.trim()
                if (source !in matched) matched += source
            }
        }

        fun readText(file: File): Probe<String> {
            return try {
                when (val stat = statProbe(file)) {
                    Stat.MISSING -> return Probe.Missing
                    Stat.RESTRICTED -> return Probe.Restricted("Permission denied")
                    Stat.AVAILABLE -> Unit
                }
                Probe.Available(file.readText())
            } catch (e: SecurityException) {
                Probe.Restricted(e.message ?: "Permission denied")
            } catch (e: Exception) {
                if (isPermissionError(e)) Probe.Restricted(e.message ?: "Permission denied")
                else Probe.Unavailable(e.message ?: "Read failed")
            }
        }

        fun capability(node: File, name: String): Capability {
            val file = File(node, name)
            return when (val probe = readText(file)) {
                is Probe.Available -> Capability(name, CapabilityState.AVAILABLE, file.absolutePath)
                is Probe.Restricted -> {
                    restrictedEvidence += name
                    Capability(name, CapabilityState.RESTRICTED, file.absolutePath, probe.reason)
                }
                is Probe.Unavailable -> Capability(name, CapabilityState.UNAVAILABLE, file.absolutePath, probe.reason)
                Probe.Missing -> Capability(name, CapabilityState.UNAVAILABLE, file.absolutePath, "Not present")
            }
        }

        fun probe(name: String): Pair<File, Map<String, Capability>>? {
            val node = File(root, name)
            if (!isDirectory(node)) return null
            nodes += name

            // The LED node name itself is valid driver evidence for known AWINIC nodes.
            addDriverEvidence(name, name)

            val driverLink = File(node, "device/driver")
            when (val probe = tryCanonicalName(driverLink)) {
                is Probe.Available -> addDriverEvidence(probe.value, name)
                is Probe.Restricted -> restrictedEvidence += "device/driver"
                else -> Unit
            }

            when (val probe = readText(File(node, "device/uevent"))) {
                is Probe.Available -> probe.value.lineSequence()
                    .firstOrNull { it.startsWith("DRIVER=", ignoreCase = true) }
                    ?.substringAfter('=')
                    ?.let { addDriverEvidence(it, name) }
                is Probe.Restricted -> restrictedEvidence += "uevent"
                else -> Unit
            }

            when (val probe = readText(File(node, "device/of_node/compatible"))) {
                is Probe.Available -> addDriverEvidence(probe.value, name)
                is Probe.Restricted -> restrictedEvidence += "of_node/compatible"
                else -> Unit
            }

            val caps = allCapabilities.associateWith { capability(node, it) }
            return node to caps
        }

        val probed = linkedMapOf<String, Map<String, Capability>>()

        // Known nodes first: direct probing still works when listFiles() is blocked.
        for (name in knownNodes) {
            probe(name)?.let { (_, caps) -> probed[name] = caps }
        }

        // Enumerated OEM/custom nodes are a fallback.
        for (name in nodes.toList()) {
            if (name in probed) continue
            probe(name)?.let { (_, caps) ->
                if (driverPattern.containsMatchIn(name) || matched.contains(name)) {
                    probed[name] = caps
                }
            }
        }

        // Additional driver evidence outside the LED class.
        runCatching {
            File("/sys/module").listFiles().orEmpty().forEach { f ->
                addDriverEvidence(f.name, "module:${f.name}")
            }
        }
        runCatching {
            File("/proc/modules").takeIf { it.isFile && it.canRead() }?.forEachLine { line ->
                addDriverEvidence(line.substringBefore(' ').trim(), "proc_modules")
            }
        }
        runCatching {
            listOf("/sys/bus/i2c/drivers", "/sys/bus/platform/drivers").forEach { path ->
                File(path).listFiles().orEmpty().forEach { f ->
                    addDriverEvidence(f.name, "driver:${f.name}")
                }
            }
        }

        // Select the best node: prioritize actual core capability coverage.
        val selectedEntry = probed.entries.maxWithOrNull(
            compareBy<Map.Entry<String, Map<String, Capability>>> {
                it.value.values.count { c -> c.state == CapabilityState.AVAILABLE && c.name in coreCapabilities }
            }.thenBy {
                if (driverPattern.containsMatchIn(it.key) || matched.contains(it.key)) 1 else 0
            }.thenBy {
                it.value.values.count { c -> c.state == CapabilityState.AVAILABLE }
            }
        )

        val selected = selectedEntry?.key
        val selectedCapabilities = selectedEntry?.value ?: emptyMap()

        val driverFound = drivers.isNotEmpty() || matched.isNotEmpty()
        val minimumFunctional = listOf("brightness", "rgb", "cfg", "effect")
        val compatible = driverFound && selected != null && minimumFunctional.all {
            selectedCapabilities[it]?.state == CapabilityState.AVAILABLE
        }
        val partial = compatible && selectedCapabilities.values.any {
            it.state != CapabilityState.AVAILABLE && it.name in (coreCapabilities + optionalCapabilities)
        }

        if (nodes.isEmpty()) details += "Tidak ada LED class node yang dapat dibaca."
        else details += "${nodes.size} LED node terdeteksi."
        if (drivers.isNotEmpty()) details += "Bukti driver: ${drivers.joinToString()}"
        if (matched.isNotEmpty()) details += "Node/evidence cocok: ${matched.joinToString()}"

        if (selected != null) {
            details += "Node aktif: $selected"
            val coreMissing = coreCapabilities.filter {
                selectedCapabilities[it]?.state == CapabilityState.UNAVAILABLE
            }
            val coreRestricted = coreCapabilities.filter {
                selectedCapabilities[it]?.state == CapabilityState.RESTRICTED
            }
            val optionalMissing = optionalCapabilities.filter {
                selectedCapabilities[it]?.state == CapabilityState.UNAVAILABLE
            }
            if (coreMissing.isNotEmpty()) details += "Core unavailable: ${coreMissing.joinToString()}"
            if (coreRestricted.isNotEmpty()) details += "Core restricted: ${coreRestricted.joinToString()}"
            if (optionalMissing.isNotEmpty()) details += "Optional unavailable: ${optionalMissing.joinToString()}"
        } else if (driverFound) {
            details += "Bukti driver ada, tetapi tidak ada LED node yang memenuhi minimum functional interface."
        } else {
            details += "Tidak ditemukan bukti driver AWINIC/AW22XXX."
        }

        if (restrictedEvidence.isNotEmpty()) {
            details += "Permission restricted: ${restrictedEvidence.joinToString()}"
        }

        val finalCapabilities = if (selected != null) selectedCapabilities else {
            allCapabilities.associateWith { Capability(it, CapabilityState.UNAVAILABLE) }
        }

        return Result(
            compatible = compatible,
            partial = partial,
            driverFound = driverFound,
            driverState = when {
                driverFound -> if (restrictedEvidence.isNotEmpty()) CapabilityState.RESTRICTED else CapabilityState.AVAILABLE
                else -> CapabilityState.UNAVAILABLE
            },
            accessOk = enumerationAvailable || selected != null,
            ledNodes = nodes.toList().sorted(),
            driverNames = drivers.toList().sorted(),
            matchedNodes = matched.toList().sorted(),
            selectedNode = selected,
            capabilities = finalCapabilities,
            details = details,
            interfaceStatus = finalCapabilities
                .filterKeys { it in coreCapabilities + optionalCapabilities }
                .mapValues { it.value.state == CapabilityState.AVAILABLE },
        )
    }

    private sealed interface Probe<out T> {
        data class Available<T>(val value: T) : Probe<T>
        data class Restricted(val reason: String) : Probe<Nothing>
        data class Unavailable(val reason: String) : Probe<Nothing>
        data object Missing : Probe<Nothing>
    }

    private fun isDirectory(file: File): Boolean = try {
        file.isDirectory
    } catch (_: SecurityException) {
        false
    }

    private enum class Stat { AVAILABLE, MISSING, RESTRICTED }

    private fun statProbe(file: File): Stat = try {
        Os.stat(file.absolutePath)
        Stat.AVAILABLE
    } catch (e: ErrnoException) {
        when (e.errno) {
            OsConstants.ENOENT, OsConstants.ENOTDIR -> Stat.MISSING
            OsConstants.EACCES, OsConstants.EPERM -> Stat.RESTRICTED
            else -> Stat.MISSING
        }
    } catch (_: SecurityException) {
        Stat.RESTRICTED
    }

    private fun statExists(file: File): Boolean = statProbe(file) == Stat.AVAILABLE

    private fun tryCanonicalName(file: File): Probe<String> = try {
        when (statProbe(file)) {
            Stat.AVAILABLE -> Probe.Available(file.canonicalFile.name)
            Stat.MISSING -> Probe.Missing
            Stat.RESTRICTED -> Probe.Restricted("Permission denied")
        }
    } catch (e: SecurityException) {
        Probe.Restricted(e.message ?: "Permission denied")
    } catch (e: Exception) {
        if (isPermissionError(e)) Probe.Restricted(e.message ?: "Permission denied")
        else Probe.Unavailable(e.message ?: "Unable to resolve driver")
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
}
