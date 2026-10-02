package zuanvfx01.aw22xxx_leds

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

        // Detect "phone rebooted while Dynamic colors was running" and block that feature if so.
        zuanvfx01.aw22xxx_leds.services.DynamicColorGuard.recoverIfCrashed(this)

        // Counts started activities so the UI knows when the app is really in the foreground.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                started++
                AppForeground.set(true)
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) AppForeground.set(false)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

/** Process-wide "is any activity visible" flag, no extra dependency needed. */
object AppForeground {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()
    internal fun set(value: Boolean) { _isForeground.value = value }
}
