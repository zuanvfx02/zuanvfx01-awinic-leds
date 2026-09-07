package zuanvfx01.aw22xxx_leds.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import zuanvfx01.aw22xxx_leds.R

/**
 * Top-level tabs shown in the bottom navigation bar once the app is in its "main" state.
 */
enum class AppDestination(
    val route: String,
    @field:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
    Info("info", R.string.nav_info, Icons.Filled.Info, Icons.Outlined.Info);

    companion object {
        fun fromRoute(route: String?): AppDestination? = entries.firstOrNull { it.route == route }
    }
}
