package zuanvfx01.aw22xxx_leds.compat

import android.os.Build

/**
 * Capability-based, read-only AWINIC/AW22XXX hardware probe.
 *
 * Important: this scanner never treats a permission error as "driver missing".
 * A capability can be AVAILABLE, UNAVAILABLE, or RESTRICTED. Compatibility is
 * decided from the driver evidence plus the minimum functional LED interface,
 * not from a fixed number of sysfs files.
 */
object CompatibilityScanner {
    private val driverPattern = Regex("(?i)(aw22xxx|aw210xx|awinic)")
    private val knownNodes = listOf("aw22xxx_led", "aw210xx_led", "aw22xxx", "aw210xx")

    enum class CapabilityState { AVAILABLE, UNAVAILABLE, RESTRICTED }

    /** Why the scan failed. Lets the UI say the real reason instead of a blanket "driver not found". */
    enum class Failure {
        NONE,
        /** App is not running in a privileged SELinux domain (installed as a normal app, or module not applied). */
        NOT_PRIVILEGED,
        /** LED node exists but SELinux/DAC blocked access (module sepolicy/relabel not applied yet). */
        ACCESS_DENIED,
        /** No AWINIC node/driver evidence at all. */
        NO_DRIVER,
        /** Node found but minimum interface (brightness/rgb/cfg/effect) is incomplete. */
        INTERFACE_INCOMPLETE,
    }

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
        val failure: Failure = Failure.NONE,
        val appDomain: String = readSelfDomain(),
        val apkPath: String = readApkPath(),
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

    /**
     * Probes the hardware.
     *
     * [access], [device] and [paths] default to the real phone. Unit tests pass a fake
     * file system and fixed device facts, so no android.* class is touched on that path.
     */
    fun scan(
        access: SysfsAccess = AndroidSysfsAccess,
        device: DeviceInfo = AndroidSysfsAccess.currentDeviceInfo(),
        paths: ScanPaths = ScanPaths(),
    ): Result {
        val ledRoot = paths.ledClass
        val details = mutableListOf<String>()
        val nodes = linkedSetOf<String>()
        val drivers = linkedSetOf<String>()
        val matched = linkedSetOf<String>()
        val restrictedEvidence = linkedSetOf<String>()
        val deniedNodes = linkedSetOf<String>()
        var enumerationAvailable = false

        val listed = access.list(ledRoot)
        if (listed != null) {
            enumerationAvailable = true
            listed.filter { access.isDirectory("$ledRoot/$it") }.forEach { nodes += it }
        } else {
            details += "Enumerasi $ledRoot dibatasi; direct probe tetap digunakan."
        }

        fun addDriverEvidence(value: String?, source: String) {
            if (value.isNullOrBlank()) return
            if (driverPattern.containsMatchIn(value)) {
                drivers += value.trim()
                if (source !in matched) matched += source
            }
        }

        fun readText(path: String): Probe<String> = when (val r = access.read(path)) {
            is SysfsAccess.Read.Ok -> Probe.Available(r.text)
            SysfsAccess.Read.Missing -> Probe.Missing
            is SysfsAccess.Read.Denied -> Probe.Restricted(r.reason)
            is SysfsAccess.Read.Failed -> Probe.Unavailable(r.reason)
        }

        fun capability(nodePath: String, name: String): Capability {
            val path = "$nodePath/$name"
            return when (val probe = readText(path)) {
                is Probe.Available -> Capability(name, CapabilityState.AVAILABLE, path)
                is Probe.Restricted -> {
                    restrictedEvidence += name
                    Capability(name, CapabilityState.RESTRICTED, path, probe.reason)
                }
                is Probe.Unavailable -> Capability(name, CapabilityState.UNAVAILABLE, path, probe.reason)
                Probe.Missing -> Capability(name, CapabilityState.UNAVAILABLE, path, "Not present")
            }
        }

        fun probe(name: String): Map<String, Capability>? {
            val nodePath = "$ledRoot/$name"
            // NOTE: File.isDirectory() swallows EACCES and returns false, which used to make an
            // SELinux-denied node look like "driver not found". Use stat() and keep the errno.
            when (access.stat(nodePath)) {
                SysfsAccess.Stat.MISSING -> {
                    if (name in knownNodes) details += "stat $nodePath: ${access.errno(nodePath)}"
                    return null
                }
                SysfsAccess.Stat.RESTRICTED -> {
                    restrictedEvidence += "node:$name"
                    deniedNodes += name
                    details += "stat $nodePath: ${access.errno(nodePath)} (blocked by SELinux/permission)"
                }
                SysfsAccess.Stat.AVAILABLE -> if (!access.isDirectory(nodePath)) return null
            }
            nodes += name

            // The LED node name itself is valid driver evidence for known AWINIC nodes.
            addDriverEvidence(name, name)

            when (val probe = access.canonicalName("$nodePath/device/driver")) {
                is SysfsAccess.Read.Ok -> addDriverEvidence(probe.text, name)
                is SysfsAccess.Read.Denied -> restrictedEvidence += "device/driver"
                else -> Unit
            }

            when (val probe = readText("$nodePath/device/uevent")) {
                is Probe.Available -> probe.value.lineSequence()
                    .firstOrNull { it.startsWith("DRIVER=", ignoreCase = true) }
                    ?.substringAfter('=')
                    ?.let { addDriverEvidence(it, name) }
                is Probe.Restricted -> restrictedEvidence += "uevent"
                else -> Unit
            }

            when (val probe = readText("$nodePath/device/of_node/compatible")) {
                is Probe.Available -> addDriverEvidence(probe.value, name)
                is Probe.Restricted -> restrictedEvidence += "of_node/compatible"
                else -> Unit
            }

            return allCapabilities.associateWith { capability(nodePath, it) }
        }

        val probed = linkedMapOf<String, Map<String, Capability>>()

        // Known nodes first: direct probing still works when listing is blocked.
        for (name in knownNodes) {
            probe(name)?.let { caps -> probed[name] = caps }
        }

        // Enumerated OEM/custom nodes are a fallback.
        for (name in nodes.toList()) {
            if (name in probed) continue
            probe(name)?.let { caps ->
                if (driverPattern.containsMatchIn(name) || matched.contains(name)) {
                    probed[name] = caps
                }
            }
        }

        // Additional driver evidence outside the LED class.
        access.list(paths.sysModule).orEmpty().forEach { addDriverEvidence(it, "module:$it") }
        (access.read(paths.procModules) as? SysfsAccess.Read.Ok)?.text?.lineSequence()?.forEach { line ->
            addDriverEvidence(line.substringBefore(' ').trim(), "proc_modules")
        }
        paths.driverDirs.forEach { dir ->
            access.list(dir).orEmpty().forEach { addDriverEvidence(it, "driver:$it") }
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

        val domain = device.appDomain
        val apk = device.apkPath
        val privilegedDomain = domain.contains("priv_app") || domain.contains("system_app") ||
            domain.contains("platform_app") || domain.contains("system_server")
        val systemFlag = device.systemFlag
        details += "App domain: ${domain.ifBlank { "unknown" }}"
        details += "APK: ${apk.ifBlank { "unknown" }} (system flag: $systemFlag)"

        val failure = when {
            compatible -> Failure.NONE
            // A normal user-installed copy (or a module that did not apply) runs as untrusted_app,
            // which can never see the LED node. This is the #1 cause of "has LEDs but not found".
            domain.isNotBlank() && !privilegedDomain -> Failure.NOT_PRIVILEGED
            domain.isBlank() && !systemFlag -> Failure.NOT_PRIVILEGED
            deniedNodes.isNotEmpty() || (selected != null && restrictedEvidence.isNotEmpty() &&
                minimumFunctional.any { selectedCapabilities[it]?.state == CapabilityState.RESTRICTED }) -> Failure.ACCESS_DENIED
            !driverFound -> Failure.NO_DRIVER
            else -> Failure.INTERFACE_INCOMPLETE
        }
        if (failure == Failure.NOT_PRIVILEGED) {
            details += "App tidak berjalan sebagai priv-app. Hapus versi yang terpasang biasa, lalu reboot setelah flash module."
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
            accessOk = (enumerationAvailable || selected != null) && deniedNodes.isEmpty(),
            ledNodes = nodes.toList().sorted(),
            driverNames = drivers.toList().sorted(),
            matchedNodes = matched.toList().sorted(),
            selectedNode = selected,
            capabilities = finalCapabilities,
            details = details,
            interfaceStatus = finalCapabilities
                .filterKeys { it in coreCapabilities + optionalCapabilities }
                .mapValues { it.value.state == CapabilityState.AVAILABLE },
            manufacturer = device.manufacturer,
            model = device.model,
            androidVersion = device.androidVersion,
            kernel = device.kernel,
            failure = failure,
            appDomain = domain,
            apkPath = apk,
        )
    }

    private sealed interface Probe<out T> {
        data class Available<T>(val value: T) : Probe<T>
        data class Restricted(val reason: String) : Probe<Nothing>
        data class Unavailable(val reason: String) : Probe<Nothing>
        data object Missing : Probe<Nothing>
    }

    // Kept for Result's default arguments (old callers / cache code in AppScreen).
    internal fun readSelfDomain(): String = AndroidSysfsAccess.readSelfDomain()

    internal fun readApkPath(): String = AndroidSysfsAccess.readApkPath()
}
