package zuanvfx01.aw22xxx_leds.services

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

/**
 * Crash/reboot guard for Dynamic colors.
 *
 * Some AWINIC kernels hard-reset the phone when the `rgb` node is written with an LED index the
 * hardware does not have (or when it is flooded with writes). A user-space app cannot catch that,
 * so we leave a breadcrumb instead:
 *
 *  1. [arm] writes a synchronous "armed" flag + the current boot count BEFORE the first colour write.
 *  2. A clean service stop calls [disarm].
 *  3. On the next app start [recoverIfCrashed] sees "armed" with a DIFFERENT boot count -> the phone
 *     rebooted while Dynamic colors was running -> it is blocked on this device from now on, instead
 *     of rebooting the phone again every time the user taps the switch.
 *
 * A plain process kill keeps the same boot count, so it never causes a false block.
 */
object DynamicColorGuard {
    private const val TAG = "DynamicColorGuard"
    private const val PREFS = "dynamic_color_guard"
    private const val K_ARMED = "armed"
    private const val K_BOOT = "boot_count"
    private const val K_ELAPSED = "armed_elapsed"
    private const val K_BLOCKED = "blocked"

    @Volatile private var armedThisProcess = false
    @Volatile private var blockedCache: Boolean? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun bootCount(context: Context): Int = runCatching {
        Settings.Global.getInt(context.applicationContext.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrDefault(-1)

    /** True when Dynamic colors has been blocked on this device after a reboot. */
    fun isBlocked(context: Context): Boolean =
        blockedCache ?: prefs(context).getBoolean(K_BLOCKED, false).also { blockedCache = it }

    /** Cheap check for hot paths; valid after [recoverIfCrashed] / [isBlocked] ran once. */
    val blocked: Boolean get() = blockedCache == true

    /** Call once from Application.onCreate(). Returns true if a reboot was detected right now. */
    @Synchronized
    fun recoverIfCrashed(context: Context): Boolean {
        val p = prefs(context)
        blockedCache = p.getBoolean(K_BLOCKED, false)
        if (!p.getBoolean(K_ARMED, false)) return false

        val storedBoot = p.getInt(K_BOOT, -1)
        val nowBoot = bootCount(context)
        val rebooted = if (storedBoot >= 0 && nowBoot >= 0) {
            storedBoot != nowBoot
        } else {
            // Boot counter unavailable: uptime going backwards also proves a reboot.
            SystemClock.elapsedRealtime() < p.getLong(K_ELAPSED, 0L)
        }
        val editor = p.edit().putBoolean(K_ARMED, false)
        if (rebooted) editor.putBoolean(K_BLOCKED, true)
        editor.commit()
        if (rebooted) {
            blockedCache = true
            Log.w(TAG, "Reboot detected while Dynamic colors was active -> blocked on this device")
        }
        return rebooted
    }

    /** Leave the breadcrumb right before the first colour write of this process. */
    @Synchronized
    fun arm(context: Context) {
        if (armedThisProcess) return
        prefs(context).edit()
            .putBoolean(K_ARMED, true)
            .putInt(K_BOOT, bootCount(context))
            .putLong(K_ELAPSED, SystemClock.elapsedRealtime())
            .commit() // synchronous on purpose: must be on disk before the risky write
        armedThisProcess = true
    }

    /** Clean shutdown of the music service: nothing went wrong. */
    @Synchronized
    fun disarm(context: Context) {
        if (!armedThisProcess) return
        prefs(context).edit().putBoolean(K_ARMED, false).commit()
        armedThisProcess = false
    }

    /** Lets the user try again (e.g. after a kernel/ROM update). */
    @Synchronized
    fun clearBlock(context: Context) {
        prefs(context).edit().putBoolean(K_BLOCKED, false).putBoolean(K_ARMED, false).commit()
        blockedCache = false
        armedThisProcess = false
    }
}
