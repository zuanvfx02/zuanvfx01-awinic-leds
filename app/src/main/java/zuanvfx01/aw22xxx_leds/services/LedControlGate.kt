package zuanvfx01.aw22xxx_leds.services

/**
 * In-process ownership gate for the shared AWINIC sysfs LED device.
 * Music LED is the highest-priority realtime owner while microphone capture is active.
 */
object LedControlGate {
    enum class Owner { NONE, MUSIC, NOTIFICATION, CHARGER, TIMER }

    @Volatile
    var owner: Owner = Owner.NONE
        private set

    @Synchronized
    fun acquire(requester: Owner): Boolean {
        if (requester == Owner.MUSIC) {
            owner = Owner.MUSIC
            return true
        }
        if (owner == Owner.MUSIC) return false
        if (owner == Owner.NONE || owner == requester) {
            owner = requester
            return true
        }
        return false
    }

    @Synchronized
    fun release(requester: Owner) {
        if (owner == requester) owner = Owner.NONE
    }

    fun isMusicOwner(): Boolean = owner == Owner.MUSIC
}
