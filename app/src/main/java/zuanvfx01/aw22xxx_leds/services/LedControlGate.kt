package zuanvfx01.aw22xxx_leds.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for "who is holding the LED right now".
 *
 * Music LED registers itself here (acquire/release). Everything else (Charger, Timer,
 * Notification, tests) is decided by [LedResolver], which publishes its result with
 * [publishAutomation]. The UI observes [ownerFlow].
 *
 * Priority: Music > Charger > Timer > Notification (a notification is a short overlay, see
 * [LedResolver.playOverlay] and the Smart Priority setting).
 */
object LedControlGate {
    enum class Owner { NONE, MUSIC, NOTIFICATION, CHARGER, TIMER }

    @Volatile private var musicActive = false
    @Volatile private var automationOwner = Owner.NONE

    private val _ownerFlow = MutableStateFlow(Owner.NONE)
    val ownerFlow: StateFlow<Owner> = _ownerFlow.asStateFlow()

    val owner: Owner
        get() = if (musicActive) Owner.MUSIC else automationOwner

    private fun publish() {
        _ownerFlow.value = owner
    }

    @Synchronized
    fun acquire(requester: Owner): Boolean {
        if (requester == Owner.MUSIC) {
            musicActive = true
            publish()
            return true
        }
        return !musicActive
    }

    @Synchronized
    fun release(requester: Owner) {
        if (requester == Owner.MUSIC) {
            musicActive = false
            publish()
        }
    }

    /** Called by [LedResolver] only. Ignored for [Owner.MUSIC]. */
    @Synchronized
    fun publishAutomation(value: Owner) {
        automationOwner = if (value == Owner.MUSIC) Owner.NONE else value
        publish()
    }

    fun isMusicOwner(): Boolean = musicActive
}
