package zuanvfx01.aw22xxx_leds.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.receivers.ChargerReceiver

class ChargerService : Service() {

    private var chargerReceiver: ChargerReceiver? = null
    private val CHANNEL_ID = "awinic_charger_channel"

    override fun onCreate() {
        super.onCreate()
        
        // 1. Buat Notifikasi Wajib untuk Foreground Service
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Charger Monitor",
            NotificationManager.IMPORTANCE_LOW // Low = Biar gak bunyi/getar, cuma muncul diam-diam
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Charger LED Active")
            .setContentText("Monitoring charging state in background")
            // Pakai icon bawaan android buat semetara (lu bisa ganti pake icon app lu nanti)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging) 
            .build()

        startForeground(199, notification) // 199 bebas, ID unik aja

        // 2. Daftarkan Receiver Charger Secara Resmi
        chargerReceiver = ChargerReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(chargerReceiver, filter)
        Log.d("AwinicLED", "Charger Service resmi berjalan mandiri!")
    }

    override fun onDestroy() {
        super.onDestroy()
        chargerReceiver?.let { unregisterReceiver(it) }
        Log.d("AwinicLED", "Charger Service dimatikan.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
