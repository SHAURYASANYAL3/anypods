package me.anypods.app.anc

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // Additional Features state
    val conversationAwarenessEnabled = MutableStateFlow(false)
    val personalizedVolumeEnabled = MutableStateFlow(false)
    val loudSoundReductionEnabled = MutableStateFlow(true)
    val ambientDecibels = MutableStateFlow(42)

    private var transparencyJob: Job? = null
    private var adaptiveMonitorJob: Job? = null
    private var conversationMonitorJob: Job? = null
    private var personalizedVolumeJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    private var originalMediaVolume: Int? = null
    private var isDucked = false

    @SuppressLint("MissingPermission")
    fun setMode(context: Context, mode: NoiseMode) {
        _currentMode.value = mode
        Log.d(TAG, "Setting Noise Mode to: $mode")

        when (mode) {
            NoiseMode.NOISE_CANCELLATION -> {
                stopAdaptiveMonitor()
                stopTransparency()
                enableNoiseCancellation()
            }
            NoiseMode.OFF -> {
                stopAdaptiveMonitor()
                stopTransparency()
                disableNoiseCancellation()
            }
            NoiseMode.TRANSPARENCY -> {
                stopAdaptiveMonitor()
                disableNoiseCancellation()
                startTransparency(context)
            }
            NoiseMode.ADAPTIVE -> {
                startAdaptiveMonitor(context)
            }
        }
    }

    private fun enableNoiseCancellation() {
        try {
            val sessionId = 0
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                }
            }
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                }
            }
            Log.d(TAG, "Active Noise Cancellation enabled")
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable noise cancellation effects: ${e.message}")
        }
    }

    private fun disableNoiseCancellation() {
        try {
            noiseSuppressor?.enabled = false
            noiseSuppressor?.release()
            noiseSuppressor = null

            echoCanceler?.enabled = false
            echoCanceler?.release()
            echoCanceler = null
        } catch (e: Exception) {
            // Ignored
        }
    }

    @SuppressLint("MissingPermission")
    private fun startTransparency(context: Context) {
        stopTransparency()

        transparencyJob = scope.launch {
            val sampleRate = 48000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minRecordBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val minTrackBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, audioFormat)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minRecordBuf.coerceAtLeast(1024)
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minTrackBuf.coerceAtLeast(1024))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioRecord = record
            audioTrack = track

            try {
                record.startRecording()
                track.play()

                val inBuffer = ShortArray(512)
                val outBuffer = ShortArray(1024)

                while (isActive) {
                    val read = record.read(inBuffer, 0, inBuffer.size)
                    if (read > 0) {
                        val limitLoud = loudSoundReductionEnabled.value
                        val ceiling = 13000f // Loud Sound Reduction threshold to prevent ear injury

                        for (i in 0 until read) {
                            var sample = (inBuffer[i] * 1.25f)
                            if (limitLoud) {
                                // DSP soft knee compressor / peak limiter
                                if (sample > ceiling) {
                                    sample = ceiling + (sample - ceiling) * 0.15f
                                } else if (sample < -ceiling) {
                                    sample = -ceiling + (sample + ceiling) * 0.15f
                                }
                            }
                            val finalSample = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            outBuffer[2 * i] = finalSample
                            outBuffer[2 * i + 1] = finalSample
                        }
                        track.write(outBuffer, 0, read * 2)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Transparency mode stream exception: ${e.message}")
            } finally {
                try {
                    record.stop()
                    record.release()
                } catch (e: Exception) {}
                try {
                    track.stop()
                    track.release()
                } catch (e: Exception) {}
            }
        }
    }

    private fun stopTransparency() {
        transparencyJob?.cancel()
        transparencyJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioRecord = null
        audioTrack = null
    }

    /**
     * Adaptive Audio Mode:
     * Constantly monitors ambient sound. When environmental noise > 65dB (loud bus/metro/street),
     * enables Active Noise Cancellation. When in quiet environment (< 65dB), switches to Transparency.
     */
    @SuppressLint("MissingPermission")
    private fun startAdaptiveMonitor(context: Context) {
        stopAdaptiveMonitor()
        adaptiveMonitorJob = scope.launch {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf.coerceAtLeast(1024)
            )

            try {
                record.startRecording()
                val buf = ShortArray(512)
                var lastAdaptiveMode = NoiseMode.OFF

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

                        // If noise > 62 dB -> activate ANC; else Transparency
                        val targetMode = if (db > 62) NoiseMode.NOISE_CANCELLATION else NoiseMode.TRANSPARENCY
                        if (targetMode != lastAdaptiveMode) {
                            lastAdaptiveMode = targetMode
                            if (targetMode == NoiseMode.NOISE_CANCELLATION) {
                                stopTransparency()
                                enableNoiseCancellation()
                            } else {
                                disableNoiseCancellation()
                                startTransparency(context)
                            }
                        }
                    }
                    delay(500)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Adaptive monitor error: ${e.message}")
            } finally {
                try {
                    record.stop()
                    record.release()
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
     * When user starts talking, ducks music volume and enables Transparency mode.
     * When speaking stops for 2.5 seconds, restores volume!
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
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf.coerceAtLeast(1024)
            )

            try {
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
                        // Human speech voice peak threshold
                        if (maxPeak > 3200) {
                            lastSpokenTime = now
                            if (!isDucked) {
                                duckMediaVolume(context)
                            }
                        } else if (isDucked && (now - lastSpokenTime > 2500)) {
                            // User stopped speaking for 2.5 seconds
                            restoreMediaVolume(context)
                        }
                    }
                    delay(200)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Conversation awareness error: ${e.message}")
            } finally {
                try {
                    record.stop()
                    record.release()
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

    /**
     * Personalized Volume:
     * Monitors noise and adjusts volume subtly to match ambient environment.
     */
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
                delay(10000) // gentle 10s check
            }
        }
    }
}
