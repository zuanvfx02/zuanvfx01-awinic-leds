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
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import zuanvfx01.aw22xxx_leds.Application as AwinicApplication
import zuanvfx01.aw22xxx_leds.MainActivity
import zuanvfx01.aw22xxx_leds.MusicLedConfig
import zuanvfx01.aw22xxx_leds.R
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/** Music LED test service using the device microphone only. */
class MusicLedService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    // The audio capture path gets a dedicated OS thread. This avoids competing with the
    // app's normal Default dispatcher and lets us give capture/DSP a predictable priority.
    private val audioExecutor: ExecutorService = Executors.newSingleThreadExecutor(
        priorityThreadFactory("AwinicMusic-Audio", Process.THREAD_PRIORITY_AUDIO)
    )
    private val audioDispatcher = audioExecutor.asCoroutineDispatcher()

    // Sysfs is blocking I/O. Keep it on one dedicated serial thread so a slow kernel write
    // can never stall the audio capture/DSP loop or race another LED write.
    private val ledExecutor: ExecutorService = Executors.newSingleThreadExecutor(
        priorityThreadFactory("AwinicMusic-LED", Process.THREAD_PRIORITY_DISPLAY)
    )
    private val ledDispatcher = ledExecutor.asCoroutineDispatcher()

    private var analysisJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val ledWriteLock = Any()
    private val ledRequests = Channel<LedRequest>(Channel.CONFLATED)
    private var settingsJob: Job? = null
    private var ledWorkerJob: Job? = null
    @Volatile private var ledIsOn = false
    @Volatile private var cachedSettings: zuanvfx01.aw22xxx_leds.Settings? = null

    private data class LedRequest(
        val config: MusicLedConfig,
        val energyDb: Double,
        val source: MusicTelemetry.BeatSource,
        val ratio: Double,
        val strength: Double,
        val detectedAtMs: Long = SystemClock.elapsedRealtime(),
        val wake: Boolean = false,
        val restoreBrightnessOnly: Boolean = false,
    )

    private var lastLedTriggerTime = 0L
    private var lastColorChangeTime = 0L
    private var colorHue = 0f
    private var lastAudioActivityTime = 0L
    private var fadeJob: Job? = null
    @Volatile private var musicBrightness = 0
    @Volatile private var previousBrightness = -1
    @Volatile private var currentMusicBrightness = 0

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        DynamicColorGuard.recoverIfCrashed(this)
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
        analysisJob = serviceScope.launch(audioDispatcher) { runMicrophoneCapture() }
        wakeLock?.takeIf { !it.isHeld }?.runCatching { acquire() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCaptureInternal()
        restoreManualLedState()
        DynamicColorGuard.disarm(this) // clean stop: no reboot happened
        wakeLock?.takeIf { it.isHeld }?.runCatching { release() }
        LedControlGate.release(LedControlGate.Owner.MUSIC)
        activeInstance = null
        // Charger / Timer events that arrived while Music LED ran were skipped: re-check them now.
        LedResolver.requestApply(applicationContext)
        serviceScope.cancel()
        audioDispatcher.close()
        ledDispatcher.close()
        audioExecutor.shutdownNow()
        ledExecutor.shutdownNow()
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
            val initial = AwinicApplication.INSTANCE.settings.data.first()
            if (!initial.music.enabled) return
            cachedSettings = initial

            val rates = intArrayOf(48_000, 44_100, 32_000, 16_000)
            val bundle = createMicrophoneRecorder(rates) ?: error("No usable microphone AudioRecord configuration")
            val record = bundle.record
            audioRecord = record
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                error("AudioRecord entered state ${record.recordingState}")
            }

            Log.i(TAG, "MIC_CAPTURE_READY rate=${bundle.rate} buffer=${bundle.bufferBytes} hop=$HOP_SAMPLES")
            withContext(ledDispatcher) {
                previousBrightness = SysFsBridge.IO.brightness
                musicBrightness = runCatching {
                    SysFsBridge.Files.maxBrightness.readText().trim().toIntOrNull() ?: 255
                }.getOrDefault(255).coerceAtLeast(1)
                currentMusicBrightness = previousBrightness.coerceAtLeast(0)
                applyLedConfig(initial.music, initial, forceColors = true)
                restoreMusicBrightness()
                setMusicLedEnabled(true)
            }
            Log.i(TAG, "MIC_LED_PATH_READY node=${SysFsBridge.activeLedDir}")
            MusicTelemetry.onSessionStart(MIC_ACTIVITY_DB.toFloat())

            // Settings are cached so the audio loop never waits on DataStore, and LED
            // (sysfs) writes run on their own worker so a slow write cannot delay audio.
            settingsJob = AwinicApplication.INSTANCE.settings.data
                .onEach { cachedSettings = it }
                .launchIn(serviceScope)
            ledRequests.tryReceive() // drop a stale request from a previous run
            ledWorkerJob = serviceScope.launch(ledDispatcher) {
                // Beat/brightness requests share one conflated channel. This keeps the
                // worker compatible with the project's older coroutines API while still
                // guaranteeing that stale work cannot queue up behind a slow sysfs write.
                while (currentCoroutineContext().isActive) {
                    val request = ledRequests.receive()
                    triggerLedEffect(request)
                }
            }

            // DSP runs on a sliding window that advances 256 samples (~5.3 ms at 48 kHz) per read.
            // Every hop is analyzed, so onset timing resolution is one hop, not ~100 ms.
            val analyzer = AudioSpectrumAnalyzer(FRAME_SAMPLES)
            val frameMs = HOP_SAMPLES * 1000.0 / bundle.rate
            // The detector set depends on the selected beat mode and is rebuilt when it changes.
            var beatMode = BeatMode.fromId(initial.music.beatMode)
            var tracker = BeatDetectors.create(beatMode, frameMs)
            var legacy = BeatDetectors.createLegacy(beatMode)
            Log.i(TAG, "BEAT_MODE mode=$beatMode")
            val hop = ShortArray(HOP_SAMPLES)
            val frame = ShortArray(FRAME_SAMPLES)
            var lastLog = 0L
            var noDataWindows = 0
            var lastTrackerOnsetAt = -1_000_000_000L
            var levelDb = -100.0
            lastAudioActivityTime = 0L

            while (currentCoroutineContext().isActive) {
                val prefs = cachedSettings ?: initial
                val current = prefs.music
                if (!current.enabled) break
                val requestedMode = BeatMode.fromId(current.beatMode)
                if (requestedMode != beatMode) {
                    // Switching mode starts from a clean state: tempo history and beat grid
                    // from one algorithm must not leak into another.
                    beatMode = requestedMode
                    tracker = BeatDetectors.create(beatMode, frameMs)
                    legacy = BeatDetectors.createLegacy(beatMode)
                    lastTrackerOnsetAt = -1_000_000_000L
                    Log.i(TAG, "BEAT_MODE mode=$beatMode")
                }
                val read = runCatching { record.read(hop, 0, HOP_SAMPLES, AudioRecord.READ_BLOCKING) }.getOrElse {
                    Log.e(TAG, "MIC_AUDIO_READ_FAILURE", it); -1
                }
                if (read <= 0) {
                    noDataWindows++
                    if (noDataWindows % 20 == 0) Log.w(TAG, "MIC_AUDIO_NO_DATA read=$read windows=$noDataWindows")
                    delay(10); continue
                }
                noDataWindows = 0

                // Slide the analysis window by the number of samples just read.
                System.arraycopy(frame, read, frame, 0, FRAME_SAMPLES - read)
                System.arraycopy(hop, 0, frame, FRAME_SAMPLES - read, read)

                val now = SystemClock.elapsedRealtime()
                val energy = calculateEnergyDb(hop, read)
                levelDb = max(energy, levelDb - LEVEL_DECAY_DB_PER_HOP)
                val analysis = analyzer.analyze(frame, FRAME_SAMPLES, bundle.rate)

                if (now - lastLog >= 1000L) {
                    Log.d(TAG, "MIC_AUDIO_LEVEL db=${"%.1f".format(java.util.Locale.US, energy)}")
                }
                val active = energy >= MIC_ACTIVITY_DB
                if (active) {
                    lastAudioActivityTime = now
                    cancelFadeAndRestoreBrightness()
                    if (!ledIsOn) {
                        ledRequests.trySend(LedRequest(current, levelDb, MusicTelemetry.BeatSource.FALLBACK, 0.0, 0.0, wake = true))
                    }
                } else if (ledIsOn && now - lastAudioActivityTime >= SILENCE_HOLD_MS) {
                    startFadeOut()
                }

                // Spectral-flux tracker is the primary beat source. The energy detector is
                // only a conservative fallback when spectral tracking has gone quiet.
                val tracking = tracker.update(now, analysis, current.sensitivity)
                val legacyBeat = legacy.update(now, energy, current.sensitivity)
                if (tracking.onset) lastTrackerOnsetAt = now
                var beatSource: MusicTelemetry.BeatSource? = null
                var beatStrength = 0.0
                if (active) {
                    val fallbackBeat = legacyBeat &&
                        now - lastTrackerOnsetAt > beatMode.legacyFallbackMs &&
                        tracking.confidence < beatMode.fallbackMaxTempoConfidence
                    if (tracking.beat || fallbackBeat) {
                        val source = when {
                            tracking.beat && tracking.predicted -> MusicTelemetry.BeatSource.PREDICTED
                            tracking.beat -> MusicTelemetry.BeatSource.ONSET
                            else -> MusicTelemetry.BeatSource.FALLBACK
                        }
                        val strength = if (tracking.beat) tracking.strength else FALLBACK_STRENGTH
                        val ratio = if (tracking.threshold > 1e-9) tracking.flux / tracking.threshold else 0.0
                        beatSource = source
                        beatStrength = strength
                        ledRequests.trySend(LedRequest(current, levelDb, source, ratio, strength, detectedAtMs = now))
                    }
                }

                // Feeds the dashboard. Returns immediately when nothing is observing.
                MusicTelemetry.onHop(
                    nowMs = now,
                    levelDb = levelDb,
                    active = active,
                    flux = tracking.flux,
                    threshold = tracking.threshold,
                    bpm = tracking.bpm,
                    confidence = tracking.confidence,
                    phase = tracking.phase,
                    beat = beatSource,
                    strength = beatStrength,
                )

                if (now - lastLog >= 1000L) {
                    Log.d(
                        TAG,
                        "BEAT_TRACK bpm=${"%.1f".format(java.util.Locale.US, tracking.bpm)} " +
                            "conf=${"%.2f".format(java.util.Locale.US, tracking.confidence)} " +
                            "onset=${tracking.onset} beat=${tracking.beat} predicted=${tracking.predicted} " +
                            "phase=${"%.2f".format(java.util.Locale.US, tracking.phase)} " +
                            "flux=${"%.3f".format(java.util.Locale.US, analysis.spectralFlux)}"
                    )
                    lastLog = now
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.e(TAG, "MIC_PERMISSION_FAILURE", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MIC_CAPTURE_STATE_FAILURE", e)
        } catch (e: Exception) {
            Log.e(TAG, "MIC_MUSIC_LED_FAILURE", e)
        } finally {
            MusicTelemetry.onSessionEnd()
            runCatching {
                withContext(NonCancellable + ledDispatcher) {
                    setMusicLedEnabled(false)
                }
            }.onFailure { Log.w(TAG, "LED_FINAL_DISABLE_DISPATCH_FAILED", it) }
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
        val sources = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }.distinct()

        for (rate in rates) {
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) continue
            // Keep the Java-side capture buffer close to two DSP hops (~21.3 ms at 48 kHz),
            // instead of forcing a 100 ms buffer. AudioRecord/HAL may still choose its own
            // internal buffering, but we no longer add 100 ms of application-side buffering.
            val hopBytes = HOP_SAMPLES * 2
            val bufferBytes = maxOf(min, hopBytes * 2)
            for (source in sources) {
                val candidate = runCatching {
                    AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                        .setBufferSizeInBytes(bufferBytes)
                        .build()
                }.onFailure { Log.w(TAG, "MIC_AUDIO_CREATE_FAILED rate=$rate source=$source", it) }.getOrNull()
                if (candidate?.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "MIC_AUDIO_SOURCE source=$source rate=$rate buffer=$bufferBytes min=$min")
                    return RecorderBundle(candidate, rate, bufferBytes)
                }
                runCatching { candidate?.release() }
            }
        }
        return null
    }

    private fun stopCaptureInternal() {
        analysisJob?.cancel()
        analysisJob = null
        fadeJob?.cancel()
        fadeJob = null
        settingsJob?.cancel()
        settingsJob = null
        ledWorkerJob?.cancel()
        ledWorkerJob = null
        lastAudioActivityTime = 0L
        releaseAudioRecord()
        setMusicLedEnabled(false)
        MusicTelemetry.onSessionEnd()
    }

    private fun releaseAudioRecord() {
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
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

    private fun cancelFadeAndRestoreBrightness() {
        val wasFading = fadeJob?.isActive == true
        fadeJob?.cancel()
        fadeJob = null
        if (wasFading || currentMusicBrightness != musicBrightness) {
            // Never touch sysfs from the audio thread. The request is conflated so 90+
            // audio hops/sec cannot turn into 90+ blocking file writes/sec.
            val prefs = cachedSettings
            if (prefs != null) {
                ledRequests.trySend(
                    LedRequest(
                        config = prefs.music,
                        energyDb = -100.0,
                        source = MusicTelemetry.BeatSource.FALLBACK,
                        ratio = 0.0,
                        strength = 0.0,
                        wake = false,
                        restoreBrightnessOnly = true,
                    )
                )
            }
        }
    }

    private fun restoreMusicBrightness() {
        if (SysFsBridge.isPresent && SysFsBridge.supports("brightness")) {
            val target = musicBrightness.coerceAtLeast(1)
            if (currentMusicBrightness != target) {
                SysFsBridge.IO.brightness = target
                currentMusicBrightness = target
            }
        }
    }

    private fun startFadeOut() {
        if (fadeJob?.isActive == true) return
        fadeJob = serviceScope.launch(ledDispatcher) {
            val start = currentMusicBrightness.coerceAtLeast(1)
            val steps = FADE_STEPS
            for (step in 1..steps) {
                if (!currentCoroutineContext().isActive) return@launch
                val value = (start * (steps - step) / steps).coerceAtLeast(0)
                if (SysFsBridge.isPresent && SysFsBridge.supports("brightness")) {
                    SysFsBridge.IO.brightness = value
                    currentMusicBrightness = value
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

    private fun triggerLedEffect(request: LedRequest) {
        val config = request.config
        val energyDb = request.energyDb
        val now = SystemClock.elapsedRealtime()
        if (!request.wake && now - lastLedTriggerTime < LED_TRIGGER_COOLDOWN_MS) return
        if (!request.wake) lastLedTriggerTime = now

        runCatching {
            if (request.restoreBrightnessOnly) {
                restoreMusicBrightness()
                return@runCatching
            }
            val prefs = cachedSettings ?: return
            val forceColors = config.useDynamicColors && (now - lastColorChangeTime >= COLOR_COOLDOWN_MS)
            val applied = applyLedConfig(config, prefs, forceColors = forceColors, energyDb = energyDb)
            // Do NOT toggle hwen off/on for every beat. Several AWINIC drivers
            // latch their effect state and become unresponsive after rapid toggles.
            if (!ledIsOn) setMusicLedEnabled(true)
            if (request.wake) {
                restoreMusicBrightness()
                Log.d(TAG, "MIC_AUDIO_ACTIVE led=ON")
            } else {
                val completedAt = SystemClock.elapsedRealtime()
                val deliveryLatency = (completedAt - request.detectedAtMs).coerceAtLeast(0L)
                MusicTelemetry.onLedChange(
                    MusicTelemetry.LedEvent(
                        atMs = completedAt,
                        source = request.source,
                        ratio = request.ratio.toFloat(),
                        levelDb = energyDb.toFloat(),
                        effect = applied.effect,
                        frequencyHz = applied.frequencyHz,
                        detectedAtMs = request.detectedAtMs,
                        deliveryLatencyMs = deliveryLatency,
                    )
                )
                Log.d(TAG, "BEAT_TRIGGER db=${"%.1f".format(java.util.Locale.US, energyDb)} effect=${applied.effect} freq=${applied.frequencyHz} src=${request.source} latency=${deliveryLatency}ms")
            }
        }.onFailure { Log.e(TAG, "LED_BEAT_WRITE_FAILED", it) }
    }

    private data class AppliedLedState(val effect: Int, val frequencyHz: Int)

    private fun applyLedConfig(config: MusicLedConfig, prefs: zuanvfx01.aw22xxx_leds.Settings, forceColors: Boolean, energyDb: Double = -40.0): AppliedLedState = synchronized(ledWriteLock) {
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
        val targetFrequency = if (energyDb > -49.0) {
            reactiveFreq.coerceIn(minFreq, maxFreq)
        } else {
            Random.nextInt(minFreq, maxFreq + 1)
        }
        SysFsBridge.IO.frequency = targetFrequency

        val dynamic = config.useDynamicColors && dynamicColorsUsable()
        if (dynamic && forceColors) {
            applyDynamicColors()
            lastColorChangeTime = SystemClock.elapsedRealtime()
        } else if (config.useOwnValues) {
            prefs.led.rgbMap.forEach { (index, colorInt) ->
                SysFsBridge.IO.setColor(index.toUByte(), Color(colorInt))
            }
            publishOwnColors(prefs)
        }

        val useOwn = dynamic || config.useOwnValues
        SysFsBridge.flushCfg(useOwn)
        AppliedLedState(effect = effect.coerceIn(0, 255), frequencyHz = targetFrequency)
    }

    /** Dynamic colors only runs when colours are controllable, LED count is known and it is not blocked. */
    private fun dynamicColorsUsable(): Boolean =
        !DynamicColorGuard.isBlocked(this) && SysFsBridge.supports("rgb") && SysFsBridge.colorControlUsable

    private fun applyDynamicColors() {
        if (!dynamicColorsUsable()) return
        // Only the LEDs this phone really has (capped at 12), never a hard-coded 12.
        val count = SysFsBridge.ledCount.coerceIn(1, MAX_DYNAMIC_LEDS)
        // Breadcrumb first: if the kernel resets the phone, the next launch blocks this feature.
        DynamicColorGuard.arm(this)
        // Step through the hue wheel instead of choosing a random hue that may
        // look unchanged for several beats. Every update is deliberately
        // different and covers the whole RGB spectrum.
        colorHue = (colorHue + 67f) % 360f
        val spread = 288f / count
        val published = IntArray(count)
        for (i in 0 until count) {
            val color = Color.hsv((colorHue + i * spread) % 360f, 0.88f, 0.98f)
            SysFsBridge.IO.setColor(i.toUByte(), color)
            published[i] = color.toArgb()
        }
        MusicTelemetry.publishColors(published)
    }

    private fun publishOwnColors(prefs: zuanvfx01.aw22xxx_leds.Settings) {
        val map = prefs.led.rgbMap
        if (map.isEmpty()) return
        val size = (map.keys.maxOrNull() ?: return) + 1
        MusicTelemetry.publishColors(IntArray(size.coerceIn(1, 64)) { map[it] ?: 0 })
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

    private fun priorityThreadFactory(name: String, priority: Int): ThreadFactory = ThreadFactory { runnable ->
        Thread({
            runCatching { Process.setThreadPriority(priority) }
            runnable.run()
        }, name).apply { isDaemon = true }
    }

    companion object {
        private const val TAG = "AwinicMusicLED"
        private const val CHANNEL_ID = "music_led_service"
        private const val NOTIFICATION_ID = 2002
        private const val REQUEST_OPEN = 2003
        private const val REQUEST_STOP = 2004
        private const val ACTION_STOP = "zuanvfx01.aw22xxx_leds.action.STOP_MUSIC_LED"
        private const val HOP_SAMPLES = 256
        private const val FRAME_SAMPLES = 1024
        private const val LEVEL_DECAY_DB_PER_HOP = 0.5
        private const val FALLBACK_STRENGTH = 0.5
        private const val LED_TRIGGER_COOLDOWN_MS = 70L
        private const val COLOR_COOLDOWN_MS = 250L // was 120: fewer I2C writes per second
        private const val MAX_DYNAMIC_LEDS = 12
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
