package zuanvfx01.aw22xxx_leds.ui.utils

import org.json.JSONObject

data class DeviceBrand(val name: String, val models: List<String>)

/** Pure parser (org.json only) so it can be unit tested. Returns null for anything malformed. */
object CompatibleDevicesParser {
    fun parse(text: String): List<DeviceBrand>? = runCatching {
        val brands = JSONObject(text).getJSONArray("brands")
        val out = ArrayList<DeviceBrand>(brands.length())
        for (i in 0 until brands.length()) {
            val b = brands.getJSONObject(i)
            val name = b.optString("name").trim()
            val modelsJson = b.optJSONArray("models") ?: continue
            val models = (0 until modelsJson.length())
                .map { modelsJson.optString(it).trim() }
                .filter { it.isNotEmpty() }
            if (name.isNotEmpty() && models.isNotEmpty()) out += DeviceBrand(name, models)
        }
        out.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
