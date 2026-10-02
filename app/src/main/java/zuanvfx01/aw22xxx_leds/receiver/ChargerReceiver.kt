package zuanvfx01.aw22xxx_leds.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import zuanvfx01.aw22xxx_leds.services.LedResolver

/**
 * Does not touch the LED itself any more. Every power / battery / screen event just asks
 * [LedResolver] to re-read the battery state and decide what the LED should show.
 */
class ChargerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val plugged: Boolean? = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> true
            Intent.ACTION_POWER_DISCONNECTED -> false
            else -> null
        }
        val pending = goAsync()
        LedResolver.syncBattery(context, plugged) { pending.finish() }
    }
}
