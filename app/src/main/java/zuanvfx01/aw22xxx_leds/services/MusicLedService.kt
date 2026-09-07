package zuanvfx01.aw22xxx_leds.services

import android.app.Notification
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zuanvfx01.aw22xxx_leds.Application as AwinicApplication
import zuanvfx01.aw22xxx_leds.MainActivity
import zuanvfx01.aw22xxx_leds.MusicLedConfig
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.random.Random

/** Music LED test service using the device microphone only. */
class MusicLedService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var analysisJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val detectorLock = Any()
    private val energyHistory = ArrayDeque<Double>()
    private var lastBeatTime = 0L
    private var lastLedTriggerTime = 0L
    private var lastColorChangeTime = 0L
    private var colorHue = 0f
    private var ledIsOn = false
    private var lastAudioActivityTime = 0L
    private var fadeJob: Job? = null
    private var musicBrightness = 0
    private var previousBrightness = -1

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        LedControlGate.acquire(LedControlGate.Owner.MUSIC)
        createNotificationChannel()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AwinicLED::MusicLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "NOTIFICATION_STOP_REQUESTED")
            disableMusicSetting()
            stopCaptureInternal()
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            startAsForeground()
        } catch (t: Throwable) {
            Log.e(TAG, "MIC_FOREGROUND_FAILURE", t)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        stopCaptureInternal()
        analysisJob = serviceScope.launch { runMicrophoneCapture() }
        wakeLock?.takeIf { !it.isHeld }?.runCatching { acquire() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCaptureInternal()
        restoreManualLedState()
        wakeLock?.takeIf { it.isHeld }?.runCatching { release() }
        LedControlGate.release(LedControlGate.Owner.MUSIC)
        activeInstance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun restoreManualLedState() {
        runCatching {
            val prefs = kotlinx.coroutines.runBlocking { AwinicApplication.INSTANCE.settings.data.first() }
            SysFsBridge.IO.enabled = prefs.led.hwen
            SysFsBridge.IO.currentEffect = prefs.led.effect.toUByte()
            if (prefs.led.frq != 0) SysFsBridge.IO.frequency = prefs.led.frq
            if (previousBrightness >= 0 && SysFsBridge.isPresent && SysFsBridge.supports("brightness")) {
                SysFsBridge.IO.brightness = previousBrightness
            }
            previousBrightness = -1
            prefs.led.rgbMap.forEach { (index, colorInt) ->
                SysFsBridge.IO.setColor(index.toUByte(), Color(colorInt))
            }
            SysFsBridge.flushCfg(prefs.useOwnValues)
            Log.d(TAG, "MANUAL_STATE_RESTORED enabled=${prefs.led.hwen}")
        }.onFailure { Log.e(TAG, "MANUAL_STATE_RESTORE_FAILED", it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun runMicrophoneCapture() {
        try {
            val config = AwinicApplication.INSTANCE.settings.data.first().music
            if (!config.enabled) return

            val rates = intArrayOf(48_000, 44_100, 32_000, 16_000)
            val bundle = createMicrophoneRecorder(rates) ?: error("No usable microphone AudioRecord configuration")
            val record = bundle.record
            audioRecord = record
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                error("AudioRecord entered state ${record.recordingState}")
            }

            Log.i(TAG, "MIC_CAPTURE_READY rate=${bundle.rate} buffer=${bundle.bufferBytes}")
            val prefs = AwinicApplication.INSTANCE.settings.data.first()
            previousBrightness = SysFsBridge.IO.brightness
            musicBrightness = runCatching {
                SysFsBridge.Files.maxBrightness.readText().trim().toIntOrNull() ?: 255
            }.getOrDefault(255).coerceAtLeast(1)
            applyLedConfig(config, prefs, forceColors = true)
            restoreMusicBrightness()
            setMusicLedEnabled(true)
            Log.i(TAG, "MIC_LED_PATH_READY node=${SysFsBridge.activeLedDir}")

            val samples = ShortArray(bundle.bufferBytes / 2)
            var lastLog = 0L
            var noDataWindows = 0

            while (currentCoroutineContext().isActive) {
                val current = AwinicApplication.INSTANCE.settings.data.first().music
                if (!current.enabled) break
                val read = runCatching { record.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING) }.getOrElse {
                    Log.e(TAG, "MIC_AUDIO_READ_FAILURE", it); -1
                }
                if (read <= 0) {
                    noDataWindows++
                    if (noDataWindows % 20 == 0) Log.w(TAG, "MIC_AUDIO_NO_DATA read=$read windows=$noDataWindows")
                    delay(10); continue
                }
                noDataWindows = 0
                val energy = calculateEnergyDb(samples, read)
                val now = System.currentTimeMillis()
                if (now - lastLog >= 1000L) {
                    Log.d(TAG, "MIC_AUDIO_LEVEL db=${"%.1f".format(java.util.Locale.US, energy)}")
                    lastLog = now
                }
                val active = energy >= MIC_ACTIVITY_DB
                if (active) {
                    lastAudioActivityTime = now
                    cancelFadeAndRestoreBrightness()
                    if (!ledIsOn) {
                        applyLedConfig(current, AwinicApplication.INSTANCE.settings.data.first(), forceColors = true)
                        restoreMusicBrightness()
                        setMusicLedEnabled(true)
                        Log.d(TAG, "MIC_AUDIO_ACTIVE led=ON")
                    }
                } else if (ledIsOn && now - lastAudioActivityTime >= SILENCE_HOLD_MS) {
                    startFadeOut()
                }
                if (active && detectBeat(energy, current.sensitivity)) triggerLedEffect(current, energy)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "MIC_PERMISSION_FAILURE", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MIC_CAPTURE_STATE_FAILURE", e)
        } catch (e: Exception) {
            Log.e(TAG, "MIC_MUSIC_LED_FAILURE", e)
        } finally {
            setMusicLedEnabled(false)
            releaseAudioRecord()
            if (currentCoroutineContext().isActive) stopSelf()
        }
    }

    private data class RecorderBundle(
        val record: AudioRecord,
        val rate: Int,
        val bufferBytes: Int,
    )

    private fun createMicrophoneRecorder(rates: IntArray): RecorderBundle? {
        for (rate in rates) {
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) continue
            val bufferBytes = (min * 2).coerceAtLeast(rate / 10 * 2)
            val candidate = runCatching {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
            }.onFailure { Log.w(TAG, "MIC_AUDIO_CREATE_FAILED rate=$rate", it) }.getOrNull()
            if (candidate?.state == AudioRecord.STATE_INITIALIZED) return RecorderBundle(candidate, rate, bufferBytes)
            runCatching { candidate?.release() }
        }
        return null
    }

    private fun stopCaptureInternal() {
        analysisJob?.cancel()
        analysisJob = null
        fadeJob?.cancel()
        fadeJob = null
        releaseAudioRecord()
        resetDetector()
        setMusicLedEnabled(false)
    }

    private fun releaseAudioRecord() {
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    private fun resetDetector() = synchronized(detectorLock) {
        energyHistory.clear()
        lastBeatTime = 0L
        lastAudioActivityTime = 0L
    }

    private fun calculateEnergyDb(buffer: ShortArray, size: Int): Double {
        if (size <= 0) return -100.0
        var sum = 0.0
        for (i in 0 until size) {
            val sample = buffer[i].toDouble() / 32768.0
            sum += sample * sample
        }
        val rms = sqrt(sum / size)
        return if (rms > 1e-7) 20.0 * log10(rms) else -100.0
    }

    private fun detectBeat(energy: Double, sensitivity: Int): Boolean = synchronized(detectorLock) {
        energyHistory.addLast(energy)
        while (energyHistory.size > HISTORY_SIZE) energyHistory.removeFirst()
        if (energyHistory.size < MIN_HISTORY) return false

        val baselineValues = energyHistory.dropLast(1)
        val mean = baselineValues.average()
        val variance = baselineValues.map { (it - mean) * (it - mean) }.average()
        val std = sqrt(variance).coerceAtLeast(0.35)
        val s = sensitivity.coerceIn(1, 10)
        // Higher sensitivity lowers the onset requirement. Keep the detector
        // responsive to speech/music instead of requiring a large transient.
        val multiplier = 1.20 - ((s - 1) * 0.075)
        val threshold = mean + std * multiplier
        val onset = energy - mean
        val now = System.currentTimeMillis()
        if (energy < MIC_ACTIVITY_DB) return false
        if (now - lastBeatTime < MIN_BEAT_INTERVAL_MS) return false
        if (energy <= threshold || onset < MIN_ONSET_DB) return false
        lastBeatTime = now
        true
    }

    private fun cancelFadeAndRestoreBrightness() {
        fadeJob?.cancel()
        fadeJob = null
        if (ledIsOn && SysFsBridge.isPresent && SysFsBridge.supports("brightness")) {
            SysFsBridge.IO.brightness = musicBrightness.coerceAtLeast(1)
        }
    }

    private fun restoreMusicBrightness() {
        if (SysFsBridge.isPresent && SysFsBridge.supports("brightness")) {
            SysFsBridge.IO.brightness = musicBrightness.coerceAtLeast(1)
        }
    }

    private fun startFadeOut() {
        if (fadeJob?.isActive == true) return
        fadeJob = serviceScope.launch(Dispatchers.IO) {
            val start = SysFsBridge.IO.brightness.coerceAtLeast(1)
            val steps = FADE_STEPS
            for (step in 1..steps) {
                if (!currentCoroutineContext().isActive) return@launch
                val value = (start * (steps - step) / steps).coerceAtLeast(0)
                if (SysFsBridge.isPresent && SysFsBridge.supports("brightness")) {
                    SysFsBridge.IO.brightness = value
                }
                delay(FADE_STEP_MS)
            }
            if (currentCoroutineContext().isActive) {
                setMusicLedEnabled(false)
                Log.d(TAG, "MIC_SILENCE_FADE_COMPLETE led=OFF")
            }
            fadeJob = null
        }
    }

    private fun setMusicLedEnabled(enabled: Boolean) {
        runCatching {
            val ok = SysFsBridge.IO.setMasterEnabled(enabled)
            ledIsOn = enabled && ok
            if (!ok) Log.e(TAG, "LED_MASTER_WRITE_FAILED enabled=$enabled node=${SysFsBridge.activeLedDir}")
        }.onFailure {
            Log.e(TAG, "LED_MASTER_EXCEPTION enabled=$enabled", it)
            ledIsOn = false
        }
    }

    private suspend fun triggerLedEffect(config: MusicLedConfig, energyDb: Double) {
        val now = System.currentTimeMillis()
        if (now - lastLedTriggerTime < LED_TRIGGER_COOLDOWN_MS) return
        lastLedTriggerTime = now

        runCatching {
            val prefs = AwinicApplication.INSTANCE.settings.data.first()
            val forceColors = config.useDynamicColors && (now - lastColorChangeTime >= COLOR_COOLDOWN_MS)
            applyLedConfig(config, prefs, forceColors = forceColors, energyDb = energyDb)
            // Do NOT toggle hwen off/on for every beat. Several AWINIC drivers
            // latch their effect state and become unresponsive after rapid toggles.
            if (!ledIsOn) setMusicLedEnabled(true)
            Log.d(TAG, "BEAT_TRIGGER db=${"%.1f".format(java.util.Locale.US, energyDb)} effect=${SysFsBridge.IO.currentEffect} freq=${SysFsBridge.IO.frequency}")
        }.onFailure { Log.e(TAG, "LED_BEAT_WRITE_FAILED", it) }
    }

    private fun applyLedConfig(config: MusicLedConfig, prefs: zuanvfx01.aw22xxx_leds.Settings, forceColors: Boolean, energyDb: Double = -40.0) {
        val effects = config.enabledEffectsList
            .ifEmpty { SysFsBridge.IO.availableEffects.map { it.index.toInt() } }
        val effect = when {
            config.useRandomEffects && effects.isNotEmpty() -> effects.random()
            else -> prefs.led.effect
        }
        SysFsBridge.IO.currentEffect = effect.coerceIn(0, 255).toUByte()

        val minFreq = config.minFrequency.coerceIn(1, 100)
        val maxFreq = config.maxFrequency.coerceIn(minFreq, 100)
        // Convert microphone energy into a visible frequency response. This makes
        // the selected effect react even when Random Effect is disabled.
        val normalized = ((energyDb + 50.0) / 35.0).coerceIn(0.0, 1.0)
        val reactiveFreq = (minFreq + ((maxFreq - minFreq) * normalized)).toInt()
        SysFsBridge.IO.frequency = if (energyDb > -49.0) reactiveFreq.coerceIn(minFreq, maxFreq) else Random.nextInt(minFreq, maxFreq + 1)

        if (config.useDynamicColors && forceColors) {
            applyDynamicColors()
            lastColorChangeTime = System.currentTimeMillis()
        } else if (config.useOwnValues) {
            prefs.led.rgbMap.forEach { (index, colorInt) ->
                SysFsBridge.IO.setColor(index.toUByte(), Color(colorInt))
            }
        }

        val useOwn = config.useDynamicColors || config.useOwnValues
        SysFsBridge.flushCfg(useOwn)
    }

    private fun applyDynamicColors() {
        if (!SysFsBridge.isPresent || !SysFsBridge.supports("rgb")) return
        // Step through the hue wheel instead of choosing a random hue that may
        // look unchanged for several beats. Every update is deliberately
        // different and covers the whole RGB spectrum.
        colorHue = (colorHue + 67f) % 360f
        for (i in 0 until 12) {
            val color = Color.hsv((colorHue + i * 24f) % 360f, 0.88f, 0.98f)
            SysFsBridge.IO.setColor(i.toUByte(), color)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Music LED Service", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Music LED tetap berjalan di background sampai dimatikan dari aplikasi atau notifikasi"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, MusicLedService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music LED Active")
            .setContentText("Listening for sound · tap Stop to turn it off")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopIntent)
            .build()
    }

    private fun disableMusicSetting() {
        runCatching {
            kotlinx.coroutines.runBlocking {
                AwinicApplication.INSTANCE.settings.updateData { settings ->
                    settings.toBuilder()
                        .setMusic(settings.music.toBuilder().setEnabled(false).build())
                        .build()
                }
            }
        }.onFailure { Log.e(TAG, "MUSIC_SETTING_DISABLE_FAILED", it) }
    }

    companion object {
        private const val TAG = "AwinicMusicLED"
        private const val CHANNEL_ID = "music_led_service"
        private const val NOTIFICATION_ID = 2002
        private const val REQUEST_OPEN = 2003
        private const val REQUEST_STOP = 2004
        private const val ACTION_STOP = "zuanvfx01.aw22xxx_leds.action.STOP_MUSIC_LED"
        private const val HISTORY_SIZE = 43
        private const val MIN_HISTORY = 10
        private const val MIN_BEAT_INTERVAL_MS = 95L
        private const val LED_TRIGGER_COOLDOWN_MS = 70L
        private const val COLOR_COOLDOWN_MS = 120L
        private const val MIN_ONSET_DB = 0.65
        private const val MIC_ACTIVITY_DB = -52.0
        private const val SILENCE_HOLD_MS = 220L
        private const val FADE_STEPS = 8
        private const val FADE_STEP_MS = 35L

        @Volatile private var activeInstance: MusicLedService? = null
        fun isRunning(context: Context): Boolean = activeInstance != null

        fun start(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(
                context, Intent(context, MusicLedService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MusicLedService::class.java))
        }
    }

}
