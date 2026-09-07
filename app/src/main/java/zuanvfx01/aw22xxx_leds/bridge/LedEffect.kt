package zuanvfx01.aw22xxx_leds.bridge

import java.io.File

data class LedEffect(val index: UByte, val name: String) {
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
