package zuanvfx01.aw22xxx_leds.ui.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar
import zuanvfx01.aw22xxx_leds.receiver.TimerReceiver

/**
 * Timer Schedule maths + alarms.
 *
 * The alarms are only "wake-ups": when one fires, [zuanvfx01.aw22xxx_leds.services.LedResolver]
 * looks at the clock ([isActiveNow]) and decides what the LED should do. That makes the schedule
 * self-healing (enable it inside the window, reboot, change timezone... all give the right result).
 *
 * Days are a bitmask: bit (Calendar.DAY_OF_WEEK - 1), Sun = 1 ... Sat = 64. 0 means "every day".
 * For an overnight window (e.g. 22:00-06:00) the window belongs to the day it STARTS on.
 */
object TimerScheduler {
    private const val ACTION_ON = "zuanvfx01.aw22xxx_leds.TIMER_ON"
    private const val ACTION_OFF = "zuanvfx01.aw22xxx_leds.TIMER_OFF"
    const val ALL_DAYS = 0x7F

    fun effectiveMask(mask: Int): Int = if (mask == 0) ALL_DAYS else (mask and ALL_DAYS)

    fun dayEnabled(mask: Int, dayOfWeek: Int): Boolean =
        (effectiveMask(mask) and (1 shl (dayOfWeek - 1))) != 0

    fun toggleDay(mask: Int, dayOfWeek: Int): Int {
        val next = effectiveMask(mask) xor (1 shl (dayOfWeek - 1))
        // Never allow "no day at all": keep the previous mask instead.
        return if (next == 0) effectiveMask(mask) else next
    }

    private fun previousDay(dow: Int): Int = if (dow == Calendar.SUNDAY) Calendar.SATURDAY else dow - 1

    /** True when [now] is inside the schedule window of an enabled day. */
    fun isActiveNow(
        startHour: Int, startMinute: Int, endHour: Int, endMinute: Int, daysMask: Int,
        now: Calendar = Calendar.getInstance(),
    ): Boolean {
        val start = startHour * 60 + startMinute
        val end = endHour * 60 + endMinute
        if (start == end) return false
        val cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val dow = now.get(Calendar.DAY_OF_WEEK)
        return if (start < end) {
            cur in start until end && dayEnabled(daysMask, dow)
        } else {
            (cur >= start && dayEnabled(daysMask, dow)) ||
                (cur < end && dayEnabled(daysMask, previousDay(dow)))
        }
    }

    /** Next moment the window opens (strictly after [from]) on an enabled day. */
    fun nextStartMillis(startHour: Int, startMinute: Int, daysMask: Int, from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        for (offset in 0..7) {
            val c = Calendar.getInstance().apply {
                timeInMillis = from
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, startHour)
                set(Calendar.MINUTE, startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (c.timeInMillis > from && dayEnabled(daysMask, c.get(Calendar.DAY_OF_WEEK))) return c.timeInMillis
        }
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Next time the clock reaches the end time (strictly after [from]), whatever the day. */
    fun nextEndMillis(endHour: Int, endMinute: Int, from: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** "2h 10m", "45m", "1d 3h". Rounds up so it never says "0m" while something is pending. */
    fun formatDuration(millis: Long): String {
        val totalMin = ((millis + 59_999L) / 60_000L).coerceAtLeast(1L)
        val d = totalMin / (24 * 60)
        val h = (totalMin % (24 * 60)) / 60
        val m = totalMin % 60
        return when {
            d > 0 -> if (h > 0) "${d}d ${h}h" else "${d}d"
            h > 0 -> if (m > 0) "${h}h ${m}m" else "${h}h"
            else -> "${m}m"
        }
    }

    fun schedule(
        context: Context,
        startHour: Int, startMinute: Int, endHour: Int, endMinute: Int,
        daysMask: Int = 0,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        setAlarm(alarmManager, nextStartMillis(startHour, startMinute, daysMask, now), pending(context, ACTION_ON, 101))
        setAlarm(alarmManager, nextEndMillis(endHour, endMinute, now), pending(context, ACTION_OFF, 102))
        Log.d("AwinicLED", "Timer Schedule alarms set")
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pending(context, ACTION_ON, 101))
        alarmManager.cancel(pending(context, ACTION_OFF, 102))
        Log.d("AwinicLED", "Timer Schedule alarms cancelled")
    }

    private fun pending(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Exact when the system allows it, otherwise a (still Doze-friendly) inexact alarm. */
    private fun setAlarm(am: AlarmManager, at: Long, pi: PendingIntent) {
        try {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            Log.w("AwinicLED", "Exact alarm denied, falling back to inexact", e)
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        }
    }
}
