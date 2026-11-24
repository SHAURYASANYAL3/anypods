package me.anypods.app.anc

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
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
    private var transparencyJob: Job? = null
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
        }

        updateAudioMonitor(context)
    }

    /**
     * Ultra-low-latency real-time Software Transparency passthrough.
     * Captures ambient sound via microphone and streams it live into the earbuds
     * with high-pass vocal filtering and soft-knee limiting.
     */
    @SuppressLint("MissingPermission")
    private fun startTransparencyPassthrough(context: Context) {
        if (transparencyJob?.isActive == true) return
        audioMonitorJob?.cancel()
        audioMonitorJob = null

        transparencyJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val channelIn = AudioFormat.CHANNEL_IN_MONO
            val channelOut = AudioFormat.CHANNEL_OUT_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minRec = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
            val minTrack = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)

            val chunk = 256
            val recBuf = maxOf(minRec, chunk * 4)
            val trackBuf = maxOf(minTrack, chunk * 4)

            var record: AudioRecord? = null
            var track: AudioTrack? = null

            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelIn,
                    encoding,
                    recBuf
                )

                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelOut)
                    .setEncoding(encoding)
                    .build()

                track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(trackBuf)
                        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        channelOut,
                        encoding,
                        trackBuf,
                        AudioTrack.MODE_STREAM
                    )
                }

                if (record.state != AudioRecord.STATE_INITIALIZED || track.state != AudioTrack.STATE_INITIALIZED) {
                    Log.w(TAG, "Transparency hardware init failed")
                    return@launch
                }

                record.startRecording()
                track.play()

                val buf = ShortArray(chunk)
                var x1 = 0f
                var y1 = 0f
                // Single-pole IIR high-pass filter ~300Hz (eliminates wind and low frequency handling rumble)
                val rc = 1.0f / (2.0f * Math.PI.toFloat() * 300.0f)
                val dt = 1.0f / sampleRate
                val alpha = rc / (rc + dt)
                val gain = 1.45f  // Vocal intelligibility boost

                Log.i(TAG, "Software Transparency Passthrough: Started low-latency ambient feed")

                var frameCounter = 0
                var sumSquare = 0.0

                while (isActive) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0) {
                        for (i in 0 until read) {
                            val inSample = buf[i].toFloat()
                            val outSample = alpha * (y1 + inSample - x1)
                            x1 = inSample
                            y1 = outSample
                            buf[i] = (outSample * gain).toInt().coerceIn(-32767, 32767).toShort()
                            sumSquare += (buf[i] * buf[i]).toDouble()
                        }
                        track.write(buf, 0, read)

                        frameCounter += read
                        if (frameCounter >= 4410) { // ~100ms interval for ambient decibels
                            val rms = Math.sqrt(sumSquare / frameCounter)
                            val db = (20 * Math.log10(maxOf(rms, 1.0))).toInt().coerceIn(30, 100)
                            ambientDecibels.value = db
                            sumSquare = 0.0
                            frameCounter = 0
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Transparency passthrough loop warning: ${e.message}")
            } finally {
                try {
                    record?.stop()
                    record?.release()
                } catch (_: Exception) {}
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
                Log.i(TAG, "Software Transparency Passthrough: Stopped")
            }
        }
    }

    private fun stopTransparencyPassthrough() {
        transparencyJob?.cancel()
        transparencyJob = null
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
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            // Hysteresis: only adapt when ambient noise has shifted noticeably
            if (kotlin.math.abs(db - lastPersonalizedDb) < 7) return

            if (db > 68 && currentVol < maxVol) {
                // Loud environment (traffic, train, crowded room): gently raise volume
                val step = if (db > 80 && currentVol + 2 <= maxVol) 2 else 1
                am.setStreamVolume(AudioManager.STREAM_MUSIC, currentVol + step, 0)
                lastPersonalizedDb = db
                Log.d(TAG, "Personalized Volume: Elevated music to ${currentVol + step} (ambient=$db dB)")
            } else if (db < 46 && currentVol > 3) {
                // Quiet environment (home, bedroom, library): soften music
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
        if (!enabled) {
            restoreMediaVolume(context)
        }
        updateAudioMonitor(context)
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
        updateAudioMonitor(context)
        Log.d(TAG, "Personalized volume toggled: $enabled")
    }
}
