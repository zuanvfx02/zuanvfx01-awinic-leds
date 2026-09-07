package zuanvfx01.aw22xxx_leds.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** System events do not directly control LED hardware. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AwinicLED", "Ignored system LED action=${intent.action}; dedicated automation controllers own LED state")
    }
}
