package me.anypods.app.anc

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
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

    private var audioMonitorJob: Job? = null
    private var originalMediaVolume: Int? = null
    private var isDucked = false
    private var lastPersonalizedDb = 50

    fun setMode(context: Context, mode: NoiseMode) {
        _currentMode.value = mode
        Log.d(TAG, "Setting Noise Mode to: $mode")

        // Play Apple acoustic feedback chime
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
                LiveTransparencyManager.stop()
                AudioEnhancer.applyNoiseCancellationMode()
            }
            NoiseMode.TRANSPARENCY -> {
                LiveTransparencyManager.start(context)
                AudioEnhancer.applyTransparencyMode()
            }
            NoiseMode.OFF -> {
                LiveTransparencyManager.stop()
                AudioEnhancer.applyOffMode()
            }
            NoiseMode.ADAPTIVE -> {
                if (ambientDecibels.value > 62) {
                    LiveTransparencyManager.stop()
                    AudioEnhancer.applyNoiseCancellationMode()
                } else {
                    LiveTransparencyManager.start(context)
                    AudioEnhancer.applyTransparencyMode()
                }
            }
        }

        updateAudioMonitor(context)
    }

    /**
     * Synchronizes NoiseMode reported by hardware stem gesture over L2CAP.
     * Updates local state and DSP without re-dispatching back to hardware.
     */
    fun syncFromHardware(mode: NoiseMode) {
        if (_currentMode.value == mode) return
        _currentMode.value = mode
        Log.d(TAG, "Hardware listening mode synchronized: $mode")
        when (mode) {
            NoiseMode.NOISE_CANCELLATION -> {
                LiveTransparencyManager.stop()
                AudioEnhancer.applyNoiseCancellationMode()
            }
            NoiseMode.TRANSPARENCY -> {
                AudioEnhancer.applyTransparencyMode()
            }
            NoiseMode.OFF -> {
                LiveTransparencyManager.stop()
                AudioEnhancer.applyOffMode()
            }
            NoiseMode.ADAPTIVE -> {
                AudioEnhancer.applyTransparencyMode()
            }
        }
    }

    /**
     * Unified Continuous Audio Monitor:
     * High efficiency, single microphone stream that simultaneously powers:
     * 1. Adaptive Audio Mode (switches ANC / Transparency on ambient noise shift)
     * 2. Conversation Awareness (ducks music when user speaks)
     * 3. Personalized Volume (adapts media playback volume to environmental loudness)
     */
    @SuppressLint("MissingPermission")
    private fun updateAudioMonitor(context: Context) {
        // When Transparency is active, TransparencyPassthrough itself processes microphone audio
        // and ambient decibels directly, avoiding concurrent AudioRecord conflicts!
        if (_currentMode.value == NoiseMode.TRANSPARENCY) {
            audioMonitorJob?.cancel()
            audioMonitorJob = null
            return
        }

        val needsMonitor = conversationAwarenessEnabled.value ||
                personalizedVolumeEnabled.value ||
                _currentMode.value == NoiseMode.ADAPTIVE

        if (!needsMonitor) {
            audioMonitorJob?.cancel()
            audioMonitorJob = null
            return
        }

        if (audioMonitorJob?.isActive == true) return

        audioMonitorJob = scope.launch {
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
                var lastAdaptiveTarget: NoiseMode? = null
                var lastVolumeAdjustTime = 0L

                while (isActive) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        var maxPeak = 0
                        for (i in 0 until read) {
                            val sample = buf[i].toInt()
                            sumSq += sample * sample
                            val abs = kotlin.math.abs(sample)
                            if (abs > maxPeak) maxPeak = abs
                        }
                        val rms = sqrt(sumSq / read)
                        val db = (20 * log10(rms.coerceAtLeast(1.0))).toInt().coerceIn(30, 105)
                        ambientDecibels.value = db

                        val now = System.currentTimeMillis()

                        // 1. Conversation Awareness
                        if (conversationAwarenessEnabled.value) {
                            if (maxPeak > 3500) {
                                lastSpokenTime = now
                                if (!isDucked) {
                                    duckMediaVolume(context)
                                    LiveTransparencyManager.start(context)
                                    AudioEnhancer.applyTransparencyMode()
                                }
                            } else if (isDucked && (now - lastSpokenTime > 2500)) {
                                restoreMediaVolume(context)
                                if (_currentMode.value != NoiseMode.TRANSPARENCY) {
                                    LiveTransparencyManager.stop()
                                }
                                setMode(context, _currentMode.value)
                            }
                        }

                        // 2. Adaptive Audio Mode
                        if (_currentMode.value == NoiseMode.ADAPTIVE) {
                            val target = if (db > 62) NoiseMode.NOISE_CANCELLATION else NoiseMode.TRANSPARENCY
                            if (target != lastAdaptiveTarget) {
                                lastAdaptiveTarget = target
                                me.anypods.app.bluetooth.BluetoothTracker.sendHardwareListeningMode(target)
                                if (target == NoiseMode.NOISE_CANCELLATION) {
                                    LiveTransparencyManager.stop()
                                    AudioEnhancer.applyNoiseCancellationMode()
                                } else {
                                    LiveTransparencyManager.start(context)
                                    AudioEnhancer.applyTransparencyMode()
                                }
                            }
                        }

                        // 3. Personalized Volume (Active volume adjustment with hysteresis)
                        if (personalizedVolumeEnabled.value && (now - lastVolumeAdjustTime > 1800)) {
                            lastVolumeAdjustTime = now
                            handlePersonalizedVolume(context, db)
                        }
                    }
                    delay(150)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Audio monitor warning: ${e.message}")
            } finally {
                try {
                    record?.stop()
                    record?.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun handlePersonalizedVolume(context: Context, db: Int) {
        if (!personalizedVolumeEnabled.value) return
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            // ponytail: Always ensure media is not muted accidentally
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            }

            // Hysteresis: only adapt when ambient noise has shifted noticeably
            if (kotlin.math.abs(db - lastPersonalizedDb) < 8) return

            if (db > 72 && currentVol < maxVol) {
                // Loud environment (traffic, train, crowded room): gently raise volume
                val step = if (db > 82 && currentVol + 2 <= maxVol) 2 else 1
                am.setStreamVolume(AudioManager.STREAM_MUSIC, currentVol + step, 0)
                lastPersonalizedDb = db
                Log.d(TAG, "Personalized Volume: Elevated music to ${currentVol + step} (ambient=$db dB)")
            } else if (db < 42 && currentVol > 8) {
                // Quiet environment: soften music slightly, but NEVER siphon below audible floor of 8
                am.setStreamVolume(AudioManager.STREAM_MUSIC, currentVol - 1, 0)
                lastPersonalizedDb = db
                Log.d(TAG, "Personalized Volume: Softened music to ${currentVol - 1} (ambient=$db dB)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Personalized volume adjustment error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleConversationAwareness(context: Context, enabled: Boolean) {
        conversationAwarenessEnabled.value = enabled
        me.anypods.app.bluetooth.AirPodsL2capClient.sendConversationAwareness(enabled)
        if (!enabled) {
            restoreMediaVolume(context)
        }
        updateAudioMonitor(context)
    }

    private fun duckMediaVolume(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (originalMediaVolume == null) {
                originalMediaVolume = if (current > 0) current else 10
            }
            val duckLevel = (originalMediaVolume!! * 0.6f).toInt().coerceAtLeast(6)
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
            val target = originalMediaVolume ?: 10
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target.coerceAtLeast(6), 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
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
        updateAudioMonitor(context)
        Log.d(TAG, "Personalized volume toggled: $enabled")
    }
}
