package zuanvfx01.aw22xxx_leds.ui.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar
import zuanvfx01.aw22xxx_leds.receiver.TimerReceiver

object TimerScheduler {
    private const val ACTION_ON = "zuanvfx01.aw22xxx_leds.TIMER_ON"
    private const val ACTION_OFF = "zuanvfx01.aw22xxx_leds.TIMER_OFF"

    fun schedule(context: Context, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 1. Jadwalkan Alarm ON
        val onIntent = Intent(context, TimerReceiver::class.java).apply { action = ACTION_ON }
        val onPendingIntent = PendingIntent.getBroadcast(context, 101, onIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val onTime = getNextTimeInMillis(startHour, startMinute)
        
        // 2. Jadwalkan Alarm OFF
        val offIntent = Intent(context, TimerReceiver::class.java).apply { action = ACTION_OFF }
        val offPendingIntent = PendingIntent.getBroadcast(context, 102, offIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val offTime = getNextTimeInMillis(endHour, endMinute)

        // Set Alarm menggunakan RTC_WAKEUP (membangunkan sistem saat jam tiba)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, onTime, onPendingIntent)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, offTime, offPendingIntent)
            Log.d("AwinicLED", "Timer Schedule berhasil dipasang!")
        } catch (e: SecurityException) {
            Log.e("AwinicLED", "Izin Alarm ditolak sistem!", e)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val onIntent = Intent(context, TimerReceiver::class.java).apply { action = ACTION_ON }
        val onPendingIntent = PendingIntent.getBroadcast(context, 101, onIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(onPendingIntent)

        val offIntent = Intent(context, TimerReceiver::class.java).apply { action = ACTION_OFF }
        val offPendingIntent = PendingIntent.getBroadcast(context, 102, offIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(offPendingIntent)
        
        Log.d("AwinicLED", "Timer Schedule dibatalkan/dimatikan.")
    }

    // Fungsi pintar buat ngecek: "Kalau jamnya udah lewat hari ini, pasang buat besok"
    private fun getNextTimeInMillis(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1) // Lempar ke besok
        }
        return calendar.timeInMillis
    }
}
