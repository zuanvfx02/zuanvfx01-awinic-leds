package zuanvfx01.aw22xxx_leds.ui.model

import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.SavedLedSettings
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.services.ChargerService
import zuanvfx01.aw22xxx_leds.services.LedControlGate
import zuanvfx01.aw22xxx_leds.ui.utils.TimerScheduler

class LedsViewModel : ViewModel() {
    private val _state = MutableStateFlow(LedsUiState.load(Application.INSTANCE))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _state.emit(LedsUiState.load(Application.INSTANCE))
                delay(1_000L)
            }
        }
    }

    suspend fun update() {
        _state.emit(LedsUiState.load(Application.INSTANCE))

        if (_state.value.useSavedSettings) {
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

    fun setEnabled(enabled: Boolean) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                it.toBuilder()
                    .setLed(it.led.toBuilder().setHwen(enabled).build())
                    .build()
            }
            if (!LedControlGate.isMusicOwner()) SysFsBridge.IO.enabled = enabled
            update()
        }
    }

    fun setUseSavedSettings(save: Boolean) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                it.toBuilder().setUseSavedSettings(save).build()
            }
            update()
        }
    }

    fun setUseOwnValues(use: Boolean) {
        runBlocking {
            Application.INSTANCE.settings.updateData { it.toBuilder().setUseOwnValues(use).build() }
            SysFsBridge.flushCfg(use)
            update()
        }
    }

    fun setEffect(index: UByte) {
        SysFsBridge.IO.currentEffect = index
        SysFsBridge.flushCfg(_state.value.useOwnValues)
        runBlocking { update() }
    }

    fun setFrequency(frequency: Int) {
        SysFsBridge.IO.frequency = frequency
        SysFsBridge.flushCfg(_state.value.useOwnValues)
        runBlocking { update() }
    }

    fun setColor(index: UByte, color: Color) {
        SysFsBridge.IO.setColor(index, color)
        SysFsBridge.flushCfg(_state.value.useOwnValues)
        runBlocking { update() }
    }

    fun setSmartOverride(enable: Boolean) {
        runBlocking {
            Application.INSTANCE.settings.updateData { 
                it.toBuilder().setSmartOverride(enable).build() 
            }
            update()
        }
    }

    // ==========================================
    // NOTIFICATION LED FUNCTIONS
    // ==========================================
    fun setEnableNotificationLed(enable: Boolean) {
        runBlocking {
            Application.INSTANCE.settings.updateData { 
                it.toBuilder().setNotification(it.notification.toBuilder().setEnabled(enable).build()).build() 
            }
            update()
        }
    }

    fun setNotificationUseOwnValues(use: Boolean) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                it.toBuilder().setNotification(it.notification.toBuilder().setUseOwnValues(use).build()).build()
            }
            update()
        }
    }

    fun setNotificationEffect(effect: Int) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                val currentLed = it.notification.led.toBuilder().setEffect(effect).build()
                it.toBuilder().setNotification(it.notification.toBuilder().setLed(currentLed).build()).build()
            }
            update()
        }
    }

    fun setNotificationFrequency(frequency: Int) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                val currentLed = it.notification.led.toBuilder().setFrq(frequency).build()
                it.toBuilder().setNotification(it.notification.toBuilder().setLed(currentLed).build()).build()
            }
            update()
        }
    }

    fun setNotificationColor(index: Int, color: Color) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                val currentLed = it.notification.led.toBuilder().putRgb(index, color.toArgb()).build()
                it.toBuilder().setNotification(it.notification.toBuilder().setLed(currentLed).build()).build()
            }
            update()
        }
    }

    // ==========================================
    // CHARGER LED FUNCTIONS
    // ==========================================
    fun setEnableChargerLed(enable: Boolean) {
        runBlocking {
            Application.INSTANCE.settings.updateData { 
                it.toBuilder().setCharger(it.charger.toBuilder().setEnabled(enable).build()).build() 
            }
            update()
        }
        
        val serviceIntent = Intent(Application.INSTANCE, ChargerService::class.java)
        if (enable) {
            Application.INSTANCE.startForegroundService(serviceIntent)
        } else {
            Application.INSTANCE.stopService(serviceIntent)
        }
    }

    fun setChargerUseOwnValues(use: Boolean) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                it.toBuilder().setCharger(it.charger.toBuilder().setUseOwnValues(use).build()).build()
            }
            update()
        }
    }

    fun setChargerEffect(effect: Int) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                val currentLed = it.charger.led.toBuilder().setEffect(effect).build()
                it.toBuilder().setCharger(it.charger.toBuilder().setLed(currentLed).build()).build()
            }
            update()
        }
    }

    fun setChargerFrequency(frequency: Int) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                val currentLed = it.charger.led.toBuilder().setFrq(frequency).build()
                it.toBuilder().setCharger(it.charger.toBuilder().setLed(currentLed).build()).build()
            }
            update()
        }
    }

    fun setChargerColor(index: Int, color: Color) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                val currentLed = it.charger.led.toBuilder().putRgb(index, color.toArgb()).build()
                it.toBuilder().setCharger(it.charger.toBuilder().setLed(currentLed).build()).build()
            }
            update()
        }
    }

    // ==========================================
    // TIMER LED FUNCTIONS
    // ==========================================
    fun setEnableTimerLed(enable: Boolean) {
        runBlocking {
            Application.INSTANCE.settings.updateData { 
                it.toBuilder().setTimer(it.timer.toBuilder().setEnabled(enable).build()).build() 
            }
            update()
        }
        
        val timerConfig = _state.value.timer
        if (enable) {
            TimerScheduler.schedule(
                Application.INSTANCE,
                timerConfig.startHour, timerConfig.startMinute,
                timerConfig.endHour, timerConfig.endMinute
            )
        } else {
            TimerScheduler.cancel(Application.INSTANCE)
        }
    }

    fun setTimerStartTime(hour: Int, minute: Int) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                it.toBuilder().setTimer(it.timer.toBuilder().setStartHour(hour).setStartMinute(minute).build()).build()
            }
            update()
        }
        if (_state.value.timer.enabled) setEnableTimerLed(true)
    }

    fun setTimerEndTime(hour: Int, minute: Int) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                it.toBuilder().setTimer(it.timer.toBuilder().setEndHour(hour).setEndMinute(minute).build()).build()
            }
            update()
        }
        if (_state.value.timer.enabled) setEnableTimerLed(true)
    }

    fun setTimerUseOwnValues(use: Boolean) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                it.toBuilder().setTimer(it.timer.toBuilder().setUseOwnValues(use).build()).build()
            }
            update()
        }
    }

    fun setTimerEffect(effect: Int) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                val currentLed = it.timer.led.toBuilder().setEffect(effect).build()
                it.toBuilder().setTimer(it.timer.toBuilder().setLed(currentLed).build()).build()
            }
            update()
        }
    }

    fun setTimerFrequency(frequency: Int) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                val currentLed = it.timer.led.toBuilder().setFrq(frequency).build()
                it.toBuilder().setTimer(it.timer.toBuilder().setLed(currentLed).build()).build()
            }
            update()
        }
    }

    fun setTimerColor(index: Int, color: Color) {
        runBlocking {
            Application.INSTANCE.settings.updateData {
                val currentLed = it.timer.led.toBuilder().putRgb(index, color.toArgb()).build()
                it.toBuilder().setTimer(it.timer.toBuilder().setLed(currentLed).build()).build()
            }
            update()
        }
    }
}
