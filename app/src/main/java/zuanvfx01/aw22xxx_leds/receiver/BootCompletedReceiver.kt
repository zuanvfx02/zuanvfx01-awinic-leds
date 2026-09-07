package zuanvfx01.aw22xxx_leds.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        p1?.action // suppress warning

        val data = Application.INSTANCE.settings.data

        if (!runBlocking { data.map { it.useSavedSettings }.first() })
            return

        val useOwnValues = runBlocking { data.map { it.useOwnValues }.first() }
        val savedData = runBlocking { data.map { it.led }.first() }

        SysFsBridge.IO.enabled = savedData.hwen
        SysFsBridge.IO.currentEffect = savedData.effect.toUByte()
        SysFsBridge.IO.frequency = savedData.frq

        for (entry in savedData.rgbMap) {
            SysFsBridge.IO.setColor(entry.key.toUByte(), Color(entry.value))
        }

        SysFsBridge.flushCfg(useOwnValues)
    }
}
