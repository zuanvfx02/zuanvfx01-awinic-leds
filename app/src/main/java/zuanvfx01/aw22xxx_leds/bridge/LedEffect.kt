package zuanvfx01.aw22xxx_leds.bridge

import java.io.File

data class LedEffect(val index: UByte, val name: String) {
    /**
     * Human-friendly name. The driver reports firmware file names such as
     * "aw22xxx_cfg_led_breath.bin"; this turns them into "Breath".
     * Falls back to the raw name if nothing is left after cleaning.
     */
    val displayName: String
        get() {
            var n = name.substringAfterLast('/').trim()
            n = n.replace(Regex("(?i)\\.(bin|hex|cfg|txt)$"), "")
            n = n.replace(Regex("(?i)^aw[0-9a-z]*?_"), "")   // aw22xxx_ / aw210xx_
            n = n.replace(Regex("(?i)^cfg_"), "")
            n = n.replace(Regex("(?i)^led_"), "")
            n = n.replace(Regex("[_\\-]+"), " ").trim()
            if (n.isEmpty()) return name
            return n.replaceFirstChar { it.uppercaseChar() }
        }

    companion object {
        fun read(file: File): List<LedEffect> = runCatching {
            if (!file.isFile || !file.canRead()) return@runCatching emptyList()
            file.readLines().mapNotNull { line ->
                val indexMatch = Regex("(?i)cfg\\[?\\s*([0-9a-f]{1,2})\\s*]?").find(line)
                    ?: return@mapNotNull null
                val index = indexMatch.groupValues[1].toIntOrNull(16)?.toUByte()
                    ?: return@mapNotNull null
                val name = line.substringAfter('=', "").trim().trim('"', '\'')
                if (name.isBlank()) null else LedEffect(index, name)
            }.distinctBy { it.index }
        }.getOrDefault(emptyList())
    }
}
