package zuanvfx01.aw22xxx_leds.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import zuanvfx01.aw22xxx_leds.receivers.ChargerReceiver

/**
 * Keeps one runtime receiver alive for power, battery-level and screen events (these cannot be
 * declared in the manifest on Android 8+). On start it also syncs the current state, so a charger
 * that was already plugged in is picked up.
 */
class ChargerService : Service() {

    private var chargerReceiver: ChargerReceiver? = null
    private val CHANNEL_ID = "awinic_charger_channel"

    override fun onCreate() {
        super.onCreate()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Charger Monitor",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Charger LED Active")
            .setContentText("Monitoring charging state in background")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .build()

        startForeground(199, notification)

        val receiver = ChargerReceiver()
        chargerReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED) // level / full, for the colour-by-level option
            addAction(Intent.ACTION_SCREEN_ON)       // "only when the screen is off" option
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        LedResolver.syncBattery(this)
        Log.d("AwinicLED", "Charger Service running")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        chargerReceiver?.let { runCatching { unregisterReceiver(it) } }
        chargerReceiver = null
        // If Charger LED was just switched off, this releases the LED back to Timer / manual.
        LedResolver.requestApply(this)
        Log.d("AwinicLED", "Charger Service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
