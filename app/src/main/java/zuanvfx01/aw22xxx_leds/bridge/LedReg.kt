package zuanvfx01.aw22xxx_leds.bridge

import java.io.File

data class LedReg(val index: UByte, val value: UByte) {
    companion object {
        fun read(file: File): List<LedReg> = runCatching {
            if (!file.isFile || !file.canRead()) return@runCatching emptyList()
            file.readLines().mapNotNull { line ->
                val bytes = Regex("(?i)0x([0-9a-f]{1,2})").findAll(line)
                    .mapNotNull { it.groupValues[1].toIntOrNull(16)?.toUByte() }.toList()
                if (bytes.size >= 2) LedReg(bytes[0], bytes[1]) else null
            }
        }.getOrDefault(emptyList())
    }
}
