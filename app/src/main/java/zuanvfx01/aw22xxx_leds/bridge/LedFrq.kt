package zuanvfx01.aw22xxx_leds.bridge

import java.io.File

object LedFrq {
    val values: List<Int> = listOf(
        0,64,128,192,256,320,384,448,512,576,640,704,768,832,896,960,
        1024,1088,1152,1216,1280,1344,1408,1472,1536,1600,1664,1728,1792,1856,
        1920,1984,2048,2112,2176,2240,2304,2368,2432,2496,2560,2624,2688,2752,
        2816,2880,2944,3008,3072,3136,3200,3264,3328,3392,3456,3520,3584,3648,
        3712,3776,3840,3904,3968,4032
    )

    fun read(file: File): Int = runCatching {
        if (!file.isFile || !file.canRead()) return@runCatching 1
        val text = file.readText()
        val hex = Regex("(?i)(?:0x)?([0-9a-f]{1,2})").findAll(text)
            .mapNotNull { it.groupValues[1].toIntOrNull(16) }
            .lastOrNull()
            ?: return@runCatching 1
        values.getOrNull(hex) ?: hex.coerceIn(1, 100)
    }.getOrDefault(1)
}
