package zuanvfx01.aw22xxx_leds.ui.model

import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zuanvfx01.aw22xxx_leds.AppForeground
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.AppLedStyle
import zuanvfx01.aw22xxx_leds.ChargerExtras
import zuanvfx01.aw22xxx_leds.NotificationExtras
import zuanvfx01.aw22xxx_leds.SavedLedSettings
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.services.ChargerService
import zuanvfx01.aw22xxx_leds.services.LedControlGate
import zuanvfx01.aw22xxx_leds.services.LedResolver
import zuanvfx01.aw22xxx_leds.ui.utils.TimerScheduler

class LedsViewModel : ViewModel() {
    // Seeded once, synchronously, so screens never see an empty effect/color list.
    // Everything after this runs off the main thread.
    private val _state = MutableStateFlow(LedsUiState.loadBlocking(Application.INSTANCE))
    val state = _state.asStateFlow()

    /**
     * All writes (DataStore + sysfs) go through one serial IO dispatcher plus a mutex, so the
     * main thread never blocks and rapid changes (e.g. dragging a color picker) are applied
     * in the order they were made.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serialIo = Dispatchers.IO.limitedParallelism(1)
    private val writeLock = Mutex()

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(serialIo) { writeLock.withLock { block() } }
    }

    init {
        // Refresh loop. Default: every 1s, always (same as before).
        // If "Polling saver" is ON: only while the app is visible.
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                Application.INSTANCE.settings.data.map { it.pollingSaver }.distinctUntilChanged(),
                AppForeground.isForeground
            ) { saver, foreground -> !saver || foreground }
                .distinctUntilChanged()
                .collectLatest { shouldPoll ->
                    if (shouldPoll) {
                        while (isActive) {
                            _state.emit(LedsUiState.load(Application.INSTANCE))
                            delay(1_000L)
                        }
                    }
                }
        }
    }

    suspend fun update() {
        _state.emit(LedsUiState.load(Application.INSTANCE))

        // While Charger / Timer / Music holds the LED, the hardware shows THEIR state: never save that
        // as the user's manual state.
        if (_state.value.useSavedSettings && LedControlGate.owner == LedControlGate.Owner.NONE) {
            Application.INSTANCE.settings.updateData {
                it.toBuilder()
                    .setLed(
                        SavedLedSettings
                            .newBuilder()
                            .setHwen(_state.value.enabled)
                            .setEffect(_state.value.currentEffect)
                            .setFrq(_state.value.frequency)
                            .putAllRgb(
                                _state.value.colors
                                    .mapIndexed { index, color -> index to color.toArgb() }
                                    .toMap()
                            )
                            .build()
                    )
                    .build()
            }
        }
    }

    fun setEnabled(enabled: Boolean) = io {
        Application.INSTANCE.settings.updateData {
            it.toBuilder()
                .setLed(it.led.toBuilder().setHwen(enabled).build())
                .build()
        }
        // Only touch the hardware when nobody else holds it; otherwise it follows when they let go.
        if (LedControlGate.owner == LedControlGate.Owner.NONE) SysFsBridge.IO.enabled = enabled
        update()
    }

    fun setUseSavedSettings(save: Boolean) = io {
        Application.INSTANCE.settings.updateData {
            it.toBuilder().setUseSavedSettings(save).build()
        }
        update()
    }

    fun setUseOwnValues(use: Boolean) = io {
        Application.INSTANCE.settings.updateData { it.toBuilder().setUseOwnValues(use).build() }
        SysFsBridge.flushCfg(use)
        update()
    }

    fun setPollingSaver(enable: Boolean) = io {
        Application.INSTANCE.settings.updateData { it.toBuilder().setPollingSaver(enable).build() }
        update()
    }

    fun setEffect(index: UByte) = io {
        SysFsBridge.IO.currentEffect = index
        SysFsBridge.flushCfg(_state.value.useOwnValues)
        update()
    }

    fun setFrequency(frequency: Int) = io {
        SysFsBridge.IO.frequency = frequency
        SysFsBridge.flushCfg(_state.value.useOwnValues)
        update()
    }

    fun setColor(index: UByte, color: Color) = io {
        SysFsBridge.IO.setColor(index, color)
        SysFsBridge.flushCfg(_state.value.useOwnValues)
        update()
    }

    fun setSmartOverride(enable: Boolean) = io {
        Application.INSTANCE.settings.updateData {
            it.toBuilder().setSmartOverride(enable).build()
        }
        update()
    }

    // ==========================================
    // NOTIFICATION LED FUNCTIONS
    // ==========================================
    fun setEnableNotificationLed(enable: Boolean) = io {
        Application.INSTANCE.settings.updateData {
            it.toBuilder().setNotification(it.notification.toBuilder().setEnabled(enable).build()).build()
        }
        update()
    }

    fun setNotificationUseOwnValues(use: Boolean) = io {
        Application.INSTANCE.settings.updateData {
            it.toBuilder().setNotification(it.notification.toBuilder().setUseOwnValues(use).build()).build()
        }
        update()
    }

    fun setNotificationEffect(effect: Int) = io {
        Application.INSTANCE.settings.updateData {
            val currentLed = it.notification.led.toBuilder().setEffect(effect).build()
            it.toBuilder().setNotification(it.notification.toBuilder().setLed(currentLed).build()).build()
        }
        update()
    }

    fun setNotificationFrequency(frequency: Int) = io {
        Application.INSTANCE.settings.updateData {
            val currentLed = it.notification.led.toBuilder().setFrq(frequency).build()
            it.toBuilder().setNotification(it.notification.toBuilder().setLed(currentLed).build()).build()
        }
        update()
    }

    fun setNotificationColor(index: Int, color: Color) = io {
        Application.INSTANCE.settings.updateData {
            val currentLed = it.notification.led.toBuilder().putRgb(index, color.toArgb()).build()
            it.toBuilder().setNotification(it.notification.toBuilder().setLed(currentLed).build()).build()
        }
        update()
    }

    // ==========================================
    // CHARGER LED FUNCTIONS
    // ==========================================
    fun setEnableChargerLed(enable: Boolean) = io {
        Application.INSTANCE.settings.updateData {
            it.toBuilder().setCharger(it.charger.toBuilder().setEnabled(enable).build()).build()
        }
        update()
        reapply()

        val serviceIntent = Intent(Application.INSTANCE, ChargerService::class.java)
        if (enable) {
            Application.INSTANCE.startForegroundService(serviceIntent)
        } else {
            Application.INSTANCE.stopService(serviceIntent)
        }
    }

    fun setChargerUseOwnValues(use: Boolean) = io {
        Application.INSTANCE.settings.updateData {
            it.toBuilder().setCharger(it.charger.toBuilder().setUseOwnValues(use).build()).build()
        }
        update()
        reapply()
    }

    fun setChargerEffect(effect: Int) = io {
        Application.INSTANCE.settings.updateData {
            val currentLed = it.charger.led.toBuilder().setEffect(effect).build()
            it.toBuilder().setCharger(it.charger.toBuilder().setLed(currentLed).build()).build()
        }
        update()
        reapply()
    }

    fun setChargerFrequency(frequency: Int) = io {
        Application.INSTANCE.settings.updateData {
            val currentLed = it.charger.led.toBuilder().setFrq(frequency).build()
            it.toBuilder().setCharger(it.charger.toBuilder().setLed(currentLed).build()).build()
        }
        update()
        reapply()
    }

    fun setChargerColor(index: Int, color: Color) = io {
        Application.INSTANCE.settings.updateData {
            val currentLed = it.charger.led.toBuilder().putRgb(index, color.toArgb()).build()
            it.toBuilder().setCharger(it.charger.toBuilder().setLed(currentLed).build()).build()
        }
        update()
        reapply()
    }

    // ==========================================
    // TIMER LED FUNCTIONS
    // ==========================================
    private fun applyTimerSchedule(enable: Boolean) {
        val timerConfig = _state.value.timer
        if (enable) {
            TimerScheduler.schedule(
                Application.INSTANCE,
                timerConfig.startHour, timerConfig.startMinute,
                timerConfig.endHour, timerConfig.endMinute, timerConfig.daysMask
            )
        } else {
            TimerScheduler.cancel(Application.INSTANCE)
        }
        // Enabling inside the window lights the LED right away; disabling hands it back.
        reapply()
    }

    fun setEnableTimerLed(enable: Boolean) = io {
        Application.INSTANCE.settings.updateData {
            it.toBuilder().setTimer(it.timer.toBuilder().setEnabled(enable).build()).build()
        }
        update()
        applyTimerSchedule(enable)
    }

    fun setTimerStartTime(hour: Int, minute: Int) = io {
        Application.INSTANCE.settings.updateData {
            it.toBuilder().setTimer(it.timer.toBuilder().setStartHour(hour).setStartMinute(minute).build()).build()
        }
        update()
        if (_state.value.timer.enabled) applyTimerSchedule(true)
    }

    fun setTimerEndTime(hour: Int, minute: Int) = io {
        Application.INSTANCE.settings.updateData {
            it.toBuilder().setTimer(it.timer.toBuilder().setEndHour(hour).setEndMinute(minute).build()).build()
        }
        update()
        if (_state.value.timer.enabled) applyTimerSchedule(true)
    }

    fun setTimerUseOwnValues(use: Boolean) = io {
        Application.INSTANCE.settings.updateData {
            it.toBuilder().setTimer(it.timer.toBuilder().setUseOwnValues(use).build()).build()
        }
        update()
        reapply()
    }

    fun setTimerEffect(effect: Int) = io {
        Application.INSTANCE.settings.updateData {
            val currentLed = it.timer.led.toBuilder().setEffect(effect).build()
            it.toBuilder().setTimer(it.timer.toBuilder().setLed(currentLed).build()).build()
        }
        update()
        reapply()
    }

    fun setTimerFrequency(frequency: Int) = io {
        Application.INSTANCE.settings.updateData {
            val currentLed = it.timer.led.toBuilder().setFrq(frequency).build()
            it.toBuilder().setTimer(it.timer.toBuilder().setLed(currentLed).build()).build()
        }
        update()
        reapply()
    }

    fun setTimerColor(index: Int, color: Color) = io {
        Application.INSTANCE.settings.updateData {
            val currentLed = it.timer.led.toBuilder().putRgb(index, color.toArgb()).build()
            it.toBuilder().setTimer(it.timer.toBuilder().setLed(currentLed).build()).build()
        }
        update()
        reapply()
    }

    // ==========================================
    // SHARED HELPERS
    // ==========================================
    /** Re-run the LED resolver after a setting that changes what Charger / Timer should show. */
    private fun reapply() = LedResolver.requestApply(Application.INSTANCE)

    /** The "Test" buttons. [onResult] runs on the main thread; false = not played (Music LED running, or busy). */
    fun testLed(kind: LedResolver.Kind, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val played = runCatching { LedResolver.playTest(Application.INSTANCE, kind) }.getOrDefault(false)
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(played) }
        }
    }

    // ==========================================
    // NOTIFICATION EXTRAS
    // ==========================================
    private fun editNotificationExtras(block: (NotificationExtras.Builder) -> Unit) = io {
        Application.INSTANCE.settings.updateData {
            val b = it.notificationExtras.toBuilder()
            block(b)
            it.toBuilder().setNotificationExtras(b.build()).build()
        }
        update()
    }

    fun setNotificationDuration(seconds: Int) = editNotificationExtras { it.setDurationS(seconds.coerceIn(1, 15)) }

    fun setNotificationOnlyScreenOff(value: Boolean) = editNotificationExtras { it.setOnlyScreenOff(value) }

    fun setNotificationRespectDnd(value: Boolean) = editNotificationExtras { it.setRespectDnd(value) }

    fun setNotificationFilterMode(mode: Int) = editNotificationExtras { it.setFilterMode(mode.coerceIn(0, 2)) }

    fun setNotificationPackages(packages: Collection<String>) = editNotificationExtras {
        it.clearPackages().addAllPackages(packages.distinct().sorted())
    }

    /** [style] = null removes the per-app override. */
    fun setNotificationAppStyle(packageName: String, style: AppStyleUi?) = editNotificationExtras {
        if (style == null) {
            it.removeAppStyles(packageName)
        } else {
            it.putAppStyles(
                packageName,
                AppLedStyle.newBuilder()
                    .setUseColor(style.useColor).setColor(style.color)
                    .setUseEffect(style.useEffect).setEffect(style.effect)
                    .build()
            )
        }
    }

    // ==========================================
    // CHARGER EXTRAS
    // ==========================================
    private fun editChargerExtras(block: (ChargerExtras.Builder) -> Unit) = io {
        Application.INSTANCE.settings.updateData {
            val b = it.chargerExtras.toBuilder()
            block(b)
            it.toBuilder().setChargerExtras(b.build()).build()
        }
        update()
        reapply()
    }

    fun setChargerLevelColors(value: Boolean) = editChargerExtras { it.setLevelColors(value) }

    fun setChargerOnlyScreenOff(value: Boolean) = editChargerExtras { it.setOnlyScreenOff(value) }

    fun setChargerLowThreshold(percent: Int) = editChargerExtras { it.setLowThreshold(percent.coerceIn(1, 99)) }

    fun setChargerHighThreshold(percent: Int) = editChargerExtras { it.setHighThreshold(percent.coerceIn(1, 99)) }

    fun setChargerLowColor(color: Color) = editChargerExtras { it.setLowColor(color.toArgb()) }

    fun setChargerHighColor(color: Color) = editChargerExtras { it.setHighColor(color.toArgb()) }

    fun setChargerFullColor(color: Color) = editChargerExtras { it.setFullColor(color.toArgb()) }

    // ==========================================
    // TIMER DAYS
    // ==========================================
    fun setTimerDays(mask: Int) = io {
        Application.INSTANCE.settings.updateData {
            it.toBuilder().setTimer(it.timer.toBuilder().setDaysMask(mask).build()).build()
        }
        update()
        if (_state.value.timer.enabled) applyTimerSchedule(true)
    }
}
