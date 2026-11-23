package me.anypods.app.anc

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.anypods.app.audio.AudioEnhancer
import kotlin.math.log10
import kotlin.math.sqrt

enum class NoiseMode {
    NOISE_CANCELLATION,
    OFF,
    TRANSPARENCY,
    ADAPTIVE
}

object NoiseControlManager {
    private const val TAG = "AnyPods.NoiseControl"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _currentMode = MutableStateFlow(NoiseMode.OFF)
    val currentMode: StateFlow<NoiseMode> = _currentMode.asStateFlow()

    val conversationAwarenessEnabled = MutableStateFlow(false)
    val personalizedVolumeEnabled = MutableStateFlow(false)
    val loudSoundReductionEnabled = MutableStateFlow(true)
    val ambientDecibels = MutableStateFlow(42)

    private var adaptiveMonitorJob: Job? = null
    private var conversationMonitorJob: Job? = null
    private var personalizedVolumeJob: Job? = null

    private var originalMediaVolume: Int? = null
    private var isDucked = false

    fun setMode(context: Context, mode: NoiseMode) {
        _currentMode.value = mode
        Log.d(TAG, "Setting Noise Mode to: $mode")

        // Play Apple acoustic feedback chime
        // Dispatch hardware listening mode to connected Bluetooth headset if supported
        me.anypods.app.bluetooth.BluetoothTracker.sendHardwareListeningMode(mode)

        when (mode) {
            NoiseMode.NOISE_CANCELLATION -> me.anypods.app.audio.AppleSoundChimes.playAncWhoosh(context)
            NoiseMode.TRANSPARENCY -> me.anypods.app.audio.AppleSoundChimes.playTransparencyTone(context)
            NoiseMode.ADAPTIVE -> me.anypods.app.audio.AppleSoundChimes.playAdaptiveTone(context)
            NoiseMode.OFF -> me.anypods.app.audio.AppleSoundChimes.playOffTone(context)
        }

        // Dispatch hardware listening mode to connected Bluetooth headset if supported
        me.anypods.app.bluetooth.BluetoothTracker.sendHardwareListeningMode(mode)

        when (mode) {
            NoiseMode.NOISE_CANCELLATION -> {
                stopAdaptiveMonitor()
                AudioEnhancer.applyNoiseCancellationMode()
            }
            NoiseMode.TRANSPARENCY -> {
                stopAdaptiveMonitor()
                AudioEnhancer.applyTransparencyMode()
            }
            NoiseMode.OFF -> {
                stopAdaptiveMonitor()
                AudioEnhancer.applyOffMode()
            }
            NoiseMode.ADAPTIVE -> {
                startAdaptiveMonitor(context)
            }
        }
    }

    /**
     * Adaptive Audio Mode:
     * Samples room/street decibels without interfering with Bluetooth music streaming.
     * When ambient noise > 65 dB, seamlessly shifts to Noise Cancellation acoustic seal.
     * When quiet (< 65 dB), shifts to open Transparency.
     */
    @SuppressLint("MissingPermission")
    private fun startAdaptiveMonitor(context: Context) {
        stopAdaptiveMonitor()
        adaptiveMonitorJob = scope.launch {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf.coerceAtLeast(1024)
                )
                record.startRecording()

                val buf = ShortArray(512)
                var lastTarget: NoiseMode? = null

                while (isActive) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            sumSq += buf[i] * buf[i]
                        }
                        val rms = sqrt(sumSq / read)
                        val db = (20 * log10(rms.coerceAtLeast(1.0))).toInt()
                        ambientDecibels.value = db

                        val target = if (db > 62) NoiseMode.NOISE_CANCELLATION else NoiseMode.TRANSPARENCY
                        if (target != lastTarget) {
                            lastTarget = target
                            if (target == NoiseMode.NOISE_CANCELLATION) {
                                AudioEnhancer.applyNoiseCancellationMode()
                            } else {
                                AudioEnhancer.applyTransparencyMode()
                            }
                        }
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Adaptive monitor error: ${e.message}")
            } finally {
                try {
                    record?.stop()
                    record?.release()
                } catch (e: Exception) {}
            }
        }
    }

    private fun stopAdaptiveMonitor() {
        adaptiveMonitorJob?.cancel()
        adaptiveMonitorJob = null
    }

    /**
     * Conversation Awareness:
     * When you talk, smoothly ducks music volume without breaking playback.
     * When speech stops for 2.5 seconds, smoothly restores original music volume.
     */
    @SuppressLint("MissingPermission")
    fun toggleConversationAwareness(context: Context, enabled: Boolean) {
        conversationAwarenessEnabled.value = enabled
        conversationMonitorJob?.cancel()
        conversationMonitorJob = null

        if (!enabled) {
            restoreMediaVolume(context)
            return
        }

        conversationMonitorJob = scope.launch {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf.coerceAtLeast(1024)
                )
                record.startRecording()

                val buf = ShortArray(512)
                var lastSpokenTime = 0L

                while (isActive) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0) {
                        var maxPeak = 0
                        for (i in 0 until read) {
                            val abs = kotlin.math.abs(buf[i].toInt())
                            if (abs > maxPeak) maxPeak = abs
                        }

                        val now = System.currentTimeMillis()
                        if (maxPeak > 3500) {
                            lastSpokenTime = now
                            if (!isDucked) {
                                duckMediaVolume(context)
                                AudioEnhancer.applyTransparencyMode()
                            }
                        } else if (isDucked && (now - lastSpokenTime > 2500)) {
                            restoreMediaVolume(context)
                            setMode(context, _currentMode.value)
                        }
                    }
                    delay(250)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Conversation awareness error: ${e.message}")
            } finally {
                try {
                    record?.stop()
                    record?.release()
                } catch (e: Exception) {}
            }
        }
    }

    private fun duckMediaVolume(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (originalMediaVolume == null) {
                originalMediaVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
            val duckLevel = (originalMediaVolume!! * 0.35f).toInt().coerceAtLeast(1)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, duckLevel, 0)
            isDucked = true
            Log.d(TAG, "Conversation started: Music ducked to $duckLevel")
        } catch (e: Exception) {
            Log.w(TAG, "Ducking error: ${e.message}")
        }
    }

    private fun restoreMediaVolume(context: Context) {
        if (!isDucked) return
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalMediaVolume?.let {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
            }
            isDucked = false
            originalMediaVolume = null
            Log.d(TAG, "Conversation ended: Music volume restored")
        } catch (e: Exception) {
            Log.w(TAG, "Restore volume error: ${e.message}")
        }
    }

    fun togglePersonalizedVolume(context: Context, enabled: Boolean) {
        personalizedVolumeEnabled.value = enabled
        personalizedVolumeJob?.cancel()
        personalizedVolumeJob = null

        if (!enabled) return

        personalizedVolumeJob = scope.launch {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            while (isActive) {
                val db = ambientDecibels.value
                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                if (db > 70 && currentVol < maxVol) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (currentVol + 1).coerceAtMost(maxVol), 0)
                } else if (db < 40 && currentVol > 4) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (currentVol - 1).coerceAtLeast(1), 0)
                }
                delay(10000)
            }
        }
    }
}
