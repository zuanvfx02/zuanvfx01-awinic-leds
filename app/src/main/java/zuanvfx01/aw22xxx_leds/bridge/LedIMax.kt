package zuanvfx01.aw22xxx_leds.bridge

import java.io.File

object LedIMax {
    enum class Values {
        IMax2mA, IMax3mA, IMax4mA, IMax6mA, IMax9mA, IMax10mA,
        IMax15mA, IMax20mA, IMax30mA, IMax40mA, IMax45mA, IMax60mA,
        IMax75mA, Unknown
    }

    fun read(file: File): Values = runCatching {
        if (!file.isFile || !file.canRead()) return@runCatching Values.Unknown
        val numbers = Regex("(?i)0x([0-9a-f]{1,2})").findAll(file.readText())
            .mapNotNull { it.groupValues[1].toIntOrNull(16) }.toList()
        Values.entries.getOrElse(numbers.lastOrNull() ?: -1) { Values.Unknown }
    }.getOrDefault(Values.Unknown)
}
