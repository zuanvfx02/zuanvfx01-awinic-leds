package zuanvfx01.aw22xxx_leds.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge

/**
 * Quick Settings tile = the master LED switch (same setting as the switch on the Settings screen).
 * While an automation (Charger / Timer / Music) holds the LED, only the saved switch changes;
 * the hardware follows when the automation lets go.
 */
class LedTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val on = withContext(Dispatchers.IO) { Application.INSTANCE.settings.data.first().led.hwen }
            render(on)
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val target = withContext(Dispatchers.IO) {
                try {
                    val current = Application.INSTANCE.settings.data.first().led.hwen
                    val next = !current
                    Application.INSTANCE.settings.updateData {
                        it.toBuilder().setLed(it.led.toBuilder().setHwen(next).build()).build()
                    }
                    if (LedControlGate.owner == LedControlGate.Owner.NONE) SysFsBridge.IO.enabled = next
                    next
                } catch (e: Exception) {
                    Log.e("AwinicLED", "Tile toggle failed", e)
                    null
                }
            }
            if (target != null) render(target)
        }
    }

    private fun render(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (on) "On" else "Off"
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
