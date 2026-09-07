package zuanvfx01.aw22xxx_leds.ui.utils

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import zuanvfx01.aw22xxx_leds.R

@Composable
fun appText(key: String, fallback: String): String {
    val context = LocalContext.current
    LanguagePackManager.version.value
    return LanguagePackManager.get(context, key, fallback)
}

@Composable
fun appString(@StringRes id: Int, vararg args: Any): String {
    val context = LocalContext.current
    LanguagePackManager.version.value
    val key = runCatching { context.resources.getResourceEntryName(id) }.getOrNull()
        ?: return context.getString(id, *args)
    val fallback = context.getString(id, *args)
    val raw = LanguagePackManager.get(context, key, fallback)
    return if (args.isEmpty()) raw else runCatching {
        raw.format(*args)
    }.getOrDefault(raw)
}
