package zuanvfx01.aw22xxx_leds.ui.utils

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Compatible-device list.
 *
 * Source of truth is app/src/main/assets/compatible_devices.json in the repo:
 *  - it ships inside the APK (works offline),
 *  - the app refreshes a cached copy from GitHub at most once per day,
 *  - so adding a device = editing that one JSON file, no new release required.
 */
object CompatibleDevices {
    private const val ASSET = "compatible_devices.json"
    private const val CACHE = "compatible_devices.json"
    const val REMOTE_URL =
        "https://raw.githubusercontent.com/zuanvfx02/zuanvfx01-awinic-leds/main/app/src/main/assets/compatible_devices.json"
    private const val REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** Cached remote copy if valid, otherwise the bundled one. Blocking: call from IO. */
    fun load(context: Context): List<DeviceBrand> {
        val cache = File(context.filesDir, CACHE)
        if (cache.isFile) {
            runCatching { CompatibleDevicesParser.parse(cache.readText()) }.getOrNull()?.let { return it }
        }
        return runCatching {
            context.assets.open(ASSET).bufferedReader().use { CompatibleDevicesParser.parse(it.readText()) }
        }.getOrNull().orEmpty()
    }

    /** Downloads a fresh list (max once per day). True if the cache was updated. Blocking. */
    fun refresh(context: Context, force: Boolean = false): Boolean {
        val cache = File(context.filesDir, CACHE)
        if (!force && cache.isFile && System.currentTimeMillis() - cache.lastModified() < REFRESH_INTERVAL_MS) {
            return false
        }
        return runCatching {
            val c = (URL(REMOTE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Awinic-Leds")
            }
            try {
                if (c.responseCode != HttpURLConnection.HTTP_OK) return@runCatching false
                val body = c.inputStream.bufferedReader().use { it.readText() }
                // Only replace the cache with something we can actually parse.
                if (CompatibleDevicesParser.parse(body) == null) return@runCatching false
                val tmp = File(context.filesDir, "$CACHE.tmp")
                tmp.writeText(body)
                tmp.renameTo(cache)
            } finally {
                c.disconnect()
            }
        }.getOrDefault(false)
    }
}
