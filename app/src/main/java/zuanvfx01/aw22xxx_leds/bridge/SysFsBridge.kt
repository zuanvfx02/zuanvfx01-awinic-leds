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
    }

    fun supports(capability: String): Boolean =
        capabilityStates[capability] == CompatibilityScanner.CapabilityState.AVAILABLE

    fun configureLedDirectory(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val candidate = File(path)
        if (!candidate.isDirectory) return false
        configuredLedDir = candidate.absolutePath
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
        val directory: File get() = File(LED_DIR)
        val brightness: File get() = File(LED_DIR, "brightness")
        val maxBrightness: File get() = File(LED_DIR, "max_brightness")
        val hwen: File get() = File(LED_DIR, "hwen")
        val reg: File get() = File(LED_DIR, "reg")
        val imax: File get() = File(LED_DIR, "imax")
        val effect: File get() = File(LED_DIR, "effect")
        val cfg: File get() = File(LED_DIR, "cfg")
        val frq: File get() = File(LED_DIR, "frq")
        val rgb: File get() = File(LED_DIR, "rgb")
        val trigger: File get() = File(LED_DIR, "trigger")
    }

    private fun File.safeRead(default: String = ""): String = runCatching {
        if (!isFile || !canRead()) return@runCatching default
        readText()
    }.getOrDefault(default)

    private fun File.safeWrite(value: String): Boolean = runCatching {
        if (!isFile || !canWrite()) return@runCatching false
        writeText(value)
        true
    }.getOrDefault(false)

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
                    Files.brightness.safeWrite(value.coerceAtLeast(0).toString())
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
            set(value) { if (capabilityStates.isEmpty() || supports("hwen")) Files.hwen.safeWrite(if (value) "1" else "0") }

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
            set(hz) { if (capabilityStates.isEmpty() || supports("frq")) Files.frq.safeWrite(hz.coerceIn(1, 100).toString()) }

        val colors: List<Color>
            get() = if (capabilityStates.isEmpty() || supports("rgb")) runCatching { LedColors.read(Files.rgb) }
                .getOrDefault(listOf(Color.Black)) else emptyList()

        fun setColor(index: UByte, color: Color) {
            if (capabilityStates.isEmpty() || supports("rgb")) runCatching { LedColors.write(Files.rgb, index, color) }
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
