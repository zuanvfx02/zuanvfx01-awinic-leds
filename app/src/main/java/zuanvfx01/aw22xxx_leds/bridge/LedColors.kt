package zuanvfx01.aw22xxx_leds.bridge

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import android.os.SystemClock

object LedColors {
    private val writeLock = Any()
    private val lastWrites = HashMap<String, Pair<String, Long>>()
    private const val REWRITE_AFTER_MS = 1_500L

    fun readColor(hex: String): Color = runCatching {
        val raw = hex.removePrefix("0x").removePrefix("0X").toLong(16).toUInt()
        val b = (raw and 0x00FF0000u) shr 16
        val r = (raw and 0x0000FF00u) shr 8
        val g = raw and 0x000000FFu
        Color(((0xFFu shl 24) or (r shl 16) or (g shl 8) or b).toInt())
    }.getOrDefault(Color.Black)

    fun read(file: File): List<Color> = runCatching {
        if (!file.isFile || !file.canRead()) return@runCatching listOf(Color.Black)
        file.readLines().mapNotNull { line ->
            val match = Regex("(?i)(?:0x)?([0-9a-f]{6,8})").find(line)
            match?.groupValues?.getOrNull(1)?.let(::readColor)
        }.ifEmpty { listOf(Color.Black) }
    }.getOrDefault(listOf(Color.Black))

    /** Real number of LEDs exposed by the driver (0 when unreadable). */
    fun count(file: File): Int = runCatching {
        if (!file.isFile || !file.canRead()) return@runCatching 0
        val rx = Regex("(?i)(?:0x)?([0-9a-f]{6,8})")
        file.readLines().count { rx.containsMatchIn(it) }
    }.getOrDefault(0)

    fun write(file: File, index: UByte, color: Color) {
        runCatching {
            if (!file.isFile || !file.canWrite()) return
            val argb = color.toArgb().toUInt()
            val r = (argb and 0x00FF0000u) shr 16
            val g = (argb and 0x0000FF00u) shr 8
            val b = argb and 0x000000FFu
            // Index MUST be hex: the driver parses "%x". Decimal 10/11 used to become 0x10/0x11 (= 16/17),
            // i.e. an out-of-range LED, which can crash some kernels.
            val payload = "0x${index.toString(16)} 0x${((b shl 16) or (r shl 8) or g).toString(16)}"
            val key = "${file.absolutePath}#${index.toInt()}"
            val now = SystemClock.elapsedRealtime()
            synchronized(writeLock) {
                val previous = lastWrites[key]
                if (previous != null && previous.first == payload && now - previous.second < REWRITE_AFTER_MS) return
                file.writeText(payload)
                lastWrites[key] = payload to now
            }
        }
    }
}
