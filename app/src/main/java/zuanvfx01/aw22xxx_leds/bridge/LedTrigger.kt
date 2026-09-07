package zuanvfx01.aw22xxx_leds.bridge

import java.io.File

object LedTrigger {
    fun read(file: File): Pair<List<String>, Int> = runCatching {
        if (!file.isFile || !file.canRead()) return@runCatching emptyList<String>() to 0
        var selectedIndex = 0
        val triggers = file.readText().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .mapIndexed { index, token ->
                if (token.length >= 2 && token.first() == '[' && token.last() == ']') {
                    selectedIndex = index
                    token.substring(1, token.length - 1)
                } else token
            }
        triggers to selectedIndex.coerceIn(0, (triggers.size - 1).coerceAtLeast(0))
    }.getOrDefault(emptyList<String>() to 0)

    fun write(file: File, trigger: String) {
        runCatching { if (file.isFile && file.canWrite()) file.writeText(trigger) }
    }
}
