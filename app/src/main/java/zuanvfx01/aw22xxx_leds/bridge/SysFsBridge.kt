package zuanvfx01.aw22xxx_leds.bridge

import androidx.compose.ui.graphics.Color
import java.io.File
import zuanvfx01.aw22xxx_leds.compat.CompatibilityScanner

/**
 * Defensive bridge for the AWINIC LED sysfs ABI.
 *
 * No root shell is used here. The app talks to the same sysfs interface it already had,
 * but every read/write is guarded so a kernel with a slightly different ABI cannot crash UI.
 */
@Suppress("unused")
object SysFsBridge {
    const val DEFAULT_LED_DIR = "/sys/class/leds/aw22xxx_led"

    @Volatile
    private var configuredLedDir: String = DEFAULT_LED_DIR

    @Volatile
    private var capabilityStates: Map<String, CompatibilityScanner.CapabilityState> = emptyMap()

    private val knownNodeNames = listOf(
        "aw22xxx_led",
        "aw210xx_led",
        "aw22xxx",
        "aw210xx",
    )

    /** The node currently used by the app. */
    val LED_DIR: String
        get() {
            resolveKnownNode()
            return configuredLedDir
        }

    val activeLedDir: String
        get() = LED_DIR

    /** Configure a node selected by the compatibility probe. */
    fun configureCapabilities(capabilities: Map<String, CompatibilityScanner.Capability>) {
        capabilityStates = capabilities.mapValues { it.value.state }
        invalidateWriteCache()
    }

    fun supports(capability: String): Boolean =
        capabilityStates[capability] == CompatibilityScanner.CapabilityState.AVAILABLE

    fun configureLedDirectory(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val candidate = File(path)
        if (!candidate.isDirectory) return false
        configuredLedDir = candidate.absolutePath
        invalidateWriteCache()
        return true
    }

    /**
     * Resolve a known node directly first. This is intentionally NOT dependent on
     * listFiles(), because Android/SELinux can allow direct sysfs access while denying
     * directory enumeration.
     */
    @Synchronized
    fun resolveKnownNode(): String {
        val current = File(configuredLedDir)
        if (current.isDirectory) return configuredLedDir

        val root = File("/sys/class/leds")
        for (name in knownNodeNames) {
            val candidate = File(root, name)
            if (candidate.isDirectory) {
                configuredLedDir = candidate.absolutePath
                return configuredLedDir
            }
        }

        // Fallback enumeration only. Failure here is not fatal.
        runCatching {
            root.listFiles().orEmpty().firstOrNull { it.isDirectory }?.let {
                configuredLedDir = it.absolutePath
            }
        }
        return configuredLedDir
    }

    object Files {
        // Cache File path objects by their absolute sysfs path. We intentionally do not keep
        // open descriptors: sysfs attributes are not guaranteed to behave correctly with a
        // long-lived descriptor, and writeText() gives the kernel a fresh attribute write.
        private val cache = HashMap<String, File>()
        private val lock = Any()

        private fun file(name: String): File {
            val path = File(LED_DIR, name).absolutePath
            synchronized(lock) {
                return cache.getOrPut(path) { File(path) }
            }
        }

        val directory: File get() = file("")
        val brightness: File get() = file("brightness")
        val maxBrightness: File get() = file("max_brightness")
        val hwen: File get() = file("hwen")
        val reg: File get() = file("reg")
        val imax: File get() = file("imax")
        val effect: File get() = file("effect")
        val cfg: File get() = file("cfg")
        val frq: File get() = file("frq")
        val rgb: File get() = file("rgb")
        val trigger: File get() = file("trigger")
    }

    private fun File.safeRead(default: String = ""): String = runCatching {
        if (!isFile || !canRead()) return@runCatching default
        readText()
    }.getOrDefault(default)

    private data class WriteCacheEntry(val value: String, val atMs: Long)
    private val writeCacheLock = Any()
    private val writeCache = HashMap<String, WriteCacheEntry>()

    private fun File.safeWrite(value: String): Boolean = runCatching {
        // Do not preflight isFile/canWrite on every hot-path write. Those checks are extra
        // filesystem calls; writeText() already reports failure and is guarded by runCatching.
        writeText(value)
        true
    }.getOrDefault(false)

    private fun File.safeWriteCached(
        value: String,
        cacheKey: String = absolutePath,
        rewriteAfterMs: Long = 1_500L,
    ): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(writeCacheLock) {
            val previous = writeCache[cacheKey]
            if (previous != null && previous.value == value && now - previous.atMs < rewriteAfterMs) return true
        }
        val ok = safeWrite(value)
        if (ok) synchronized(writeCacheLock) {
            writeCache[cacheKey] = WriteCacheEntry(value, now)
        }
        return ok
    }

    fun invalidateWriteCache() {
        synchronized(writeCacheLock) { writeCache.clear() }
        ledCountCache = -1
    }

    @Volatile private var ledCountCache = -1

    /**
     * How many LEDs this phone really has, taken from the `rgb` node (one line per LED).
     * 0 = unknown/unreadable. Used to refuse writes to LEDs that do not exist.
     */
    val ledCount: Int
        get() {
            val cached = ledCountCache
            if (cached > 0) return cached
            val n = LedColors.count(Files.rgb)
            if (n > 0) ledCountCache = n
            return n
        }

    /** True when per-LED colour control is both exposed and has a known, valid LED count. */
    val colorControlUsable: Boolean
        get() = isPresent && (capabilityStates.isEmpty() || supports("rgb")) && ledCount > 0

    object IO {
        /** Best-effort hardware enable. Some AWINIC kernels expose hwen, others only brightness. */
        fun setMasterEnabled(value: Boolean): Boolean {
            if (capabilityStates.isEmpty()) {
                val hwenOk = Files.hwen.safeWrite(if (value) "1" else "0")
                if (hwenOk) return true
                return Files.brightness.safeWrite(if (value) "1" else "0")
            }
            if (supports("hwen")) return Files.hwen.safeWrite(if (value) "1" else "0")
            if (supports("brightness")) return Files.brightness.safeWrite(if (value) "1" else "0")
            return false
        }

        var brightness: Int
            get() = if (capabilityStates.isEmpty() || supports("brightness")) {
                Files.brightness.safeRead().trim().toIntOrNull() ?: 0
            } else 0
            set(value) {
                if (capabilityStates.isEmpty() || supports("brightness")) {
                    Files.brightness.safeWriteCached(value.coerceAtLeast(0).toString(), rewriteAfterMs = 1_000L)
                }
            }

        var enabled: Boolean
            get() {
                if (capabilityStates.isNotEmpty() && !supports("hwen")) return false
                val text = Files.hwen.safeRead().trim()
                return when {
                    text.contains('1') -> true
                    text.contains('0') -> false
                    else -> false
                }
            }
            set(value) { if (capabilityStates.isEmpty() || supports("hwen")) Files.hwen.safeWriteCached(if (value) "1" else "0", rewriteAfterMs = 1_000L) }

        val registers: List<LedReg>
            get() = if (capabilityStates.isEmpty() || supports("reg")) runCatching { LedReg.read(Files.reg) }.getOrDefault(emptyList()) else emptyList()

        val currentIMax: LedIMax.Values
            get() = if (capabilityStates.isEmpty() || supports("imax")) runCatching { LedIMax.read(Files.imax) }.getOrDefault(LedIMax.Values.Unknown) else LedIMax.Values.Unknown

        var currentEffect: UByte
            get() = if (capabilityStates.isEmpty() || supports("effect")) runCatching {
                parseHexValue(Files.effect.safeRead())?.toUByte() ?: 0u
            }.getOrDefault(0u) else 0u
            set(index) { if (capabilityStates.isEmpty() || supports("effect")) Files.effect.safeWrite(index.toString()) }

        val availableEffects: List<LedEffect>
            get() = if (capabilityStates.isEmpty() || supports("cfg")) runCatching { LedEffect.read(Files.cfg) }.getOrDefault(emptyList()) else emptyList()

        var frequency: Int
            get() = if (capabilityStates.isEmpty() || supports("frq")) runCatching { LedFrq.read(Files.frq) }.getOrDefault(1) else 1
            set(hz) { if (capabilityStates.isEmpty() || supports("frq")) Files.frq.safeWriteCached(hz.coerceIn(1, 100).toString(), rewriteAfterMs = 250L) }

        val colors: List<Color>
            get() = if (capabilityStates.isEmpty() || supports("rgb")) runCatching { LedColors.read(Files.rgb) }
                .getOrDefault(listOf(Color.Black)) else emptyList()

        fun setColor(index: UByte, color: Color) {
            if (!(capabilityStates.isEmpty() || supports("rgb"))) return
            // Never address an LED the hardware does not have (can reset the phone on some kernels).
            val count = ledCount
            if (count > 0 && index.toInt() >= count) return
            runCatching { LedColors.write(Files.rgb, index, color) }
        }

        val triggers: Pair<List<String>, Int>
            get() = if (capabilityStates.isEmpty() || supports("trigger")) runCatching { LedTrigger.read(Files.trigger) }
                .getOrDefault(emptyList<String>() to 0) else emptyList<String>() to 0

        fun selectTrigger(trigger: String) {
            if (capabilityStates.isEmpty() || supports("trigger")) Files.trigger.safeWrite(trigger)
        }
    }

    val isPresent: Boolean
        get() = runCatching { File(LED_DIR).isDirectory }.getOrDefault(false)

    val canAccess: Boolean
        get() = runCatching {
            isPresent && listOf(Files.hwen, Files.effect, Files.cfg, Files.frq, Files.rgb)
                .any { it.isFile && it.canRead() }
        }.getOrDefault(false)

    /**
     * True when the LED node can be traversed AND every control file that exists is writable.
     *
     * Used at boot: the Magisk module relabels/chmods the nodes only after boot_completed, so the
     * app may start before that. File.exists()/canWrite() swallow EACCES and return false, which
     * is exactly what we want here: "not ready yet, try again".
     */
    fun isWriteReady(): Boolean = runCatching {
        if (!File(resolveKnownNode()).isDirectory) return@runCatching false
        val present = listOf(Files.hwen, Files.effect, Files.cfg, Files.frq, Files.rgb)
            .filter { it.exists() }
        present.isNotEmpty() && present.all { it.canWrite() }
    }.getOrDefault(false)

    fun flushCfg(useOwnValues: Boolean) {
        if (capabilityStates.isEmpty() || supports("cfg")) Files.cfg.safeWrite(if (useOwnValues) "2" else "1")
    }

    private fun parseHexValue(raw: String): Int? {
        val matches = Regex("(?i)(?:0x)?([0-9a-f]{1,2})").findAll(raw)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull(16) }
            .toList()
        return matches.lastOrNull()
    }
}
