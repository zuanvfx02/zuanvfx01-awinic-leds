package zuanvfx01.aw22xxx_leds

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import dagger.hilt.android.HiltAndroidApp
import zuanvfx01.aw22xxx_leds.proto.SettingsSerializer

@HiltAndroidApp
class Application : Application() {

    /**
     * App-owned Settings DataStore.
     *
     * This member intentionally mirrors the old Context.settings extension so
     * services/screens can access settings without depending on extension
     * property resolution on the custom Application subclass.
     */
    val settings: DataStore<Settings> by dataStore(
        fileName = "settings.pb",
        serializer = SettingsSerializer
    )

    companion object {
        private lateinit var _INSTANCE: zuanvfx01.aw22xxx_leds.Application
        val INSTANCE get() = _INSTANCE
    }

    override fun onCreate() {
        _INSTANCE = this
        super.onCreate()
    }
}
