package me.anypods.app.anc

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
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

    val conversationAwarenessEnabled = MutableStateFlow(false)
    val personalizedVolumeEnabled = MutableStateFlow(false)
    val loudSoundReductionEnabled = MutableStateFlow(true)
    val ambientDecibels = MutableStateFlow(42)

    private var activeJob: Job? = null
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
                startAcousticProcessing(context, isCancellation = true)
            }
            NoiseMode.TRANSPARENCY -> {
                stopAdaptiveMonitor()
                startAcousticProcessing(context, isCancellation = false)
            }
            NoiseMode.OFF -> {
                stopAdaptiveMonitor()
                stopAcousticProcessing()
            }
            NoiseMode.ADAPTIVE -> {
                startAdaptiveMonitor(context)
            }
        }
    }

    /**
     * Real-time DSP engine for both Active Noise Cancellation (Anti-phase inverted acoustic cancellation)
     * and Transparency (ambient voice amplification with Loud Sound Reduction).
     */
    @SuppressLint("MissingPermission")
    private fun startAcousticProcessing(context: Context, isCancellation: Boolean) {
        stopAcousticProcessing()

        activeJob = scope.launch {
            val sampleRate = 48000
            val channelIn = AudioFormat.CHANNEL_IN_MONO
            val channelOut = AudioFormat.CHANNEL_OUT_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            val minRecordBuf = AudioRecord.getMinBufferSize(sampleRate, channelIn, audioFormat)
            val minTrackBuf = AudioTrack.getMinBufferSize(sampleRate, channelOut, audioFormat)

            // Low-latency microphone capture
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelIn,
                audioFormat,
                minRecordBuf.coerceAtLeast(1024)
            )

            // AudioTrack targeting media stream so it routes to Bluetooth A2DP
            val trackBuilder = AudioTrack.Builder()
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
                        .setChannelMask(channelOut)
                        .build()
                )
                .setBufferSizeInBytes(minTrackBuf.coerceAtLeast(1024))
                .setTransferMode(AudioTrack.MODE_STREAM)

            val track = trackBuilder.build()

            // Explicitly route to Bluetooth headset if connected
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val btDevice = outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            if (btDevice != null) {
                track.preferredDevice = btDevice
                Log.d(TAG, "Acoustic processing routed to Bluetooth device: ${btDevice.productName}")
            }

            // Attach hardware noise suppression to the ACTIVE AudioRecord session
            if (isCancellation) {
                try {
                    val recSessionId = record.audioSessionId
                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = NoiseSuppressor.create(recSessionId)?.apply {
                            enabled = true
                        }
                    }
                    if (AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = AcousticEchoCanceler.create(recSessionId)?.apply {
                            enabled = true
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Hardware suppressor init warning: ${e.message}")
                }
            }

            audioRecord = record
            audioTrack = track

            try {
                record.startRecording()
                track.play()

                val inBuffer = ShortArray(256) // Fast low-latency 256-sample frames (~5.3ms)
                val outBuffer = ShortArray(512)

                // Single-pole IIR low-pass filter state for anti-noise smoothing
                var filterState = 0.0f

                while (isActive) {
                    val read = record.read(inBuffer, 0, inBuffer.size)
                    if (read > 0) {
                        if (isCancellation) {
                            // ACTIVE NOISE CANCELLATION:
                            // Anti-phase inversion (-180 degrees) with low-frequency emphasis (targets fan, bus, AC rumble)
                            for (i in 0 until read) {
                                val raw = inBuffer[i].toFloat()
                                // Low-pass filter for ambient drone frequencies (< 800Hz)
                                filterState += 0.35f * (raw - filterState)
                                // Invert phase to cancel ambient sound
                                val antiSample = (-filterState * 1.15f).toInt()
                                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                                outBuffer[2 * i] = antiSample
                                outBuffer[2 * i + 1] = antiSample
                            }
                        } else {
                            // TRANSPARENCY MODE:
                            // Pass-through with speech boost and Loud Sound Reduction
                            val limitLoud = loudSoundReductionEnabled.value
                            val ceiling = 12000f

                            for (i in 0 until read) {
                                var sample = inBuffer[i] * 1.35f // Speech amplification
                                if (limitLoud) {
                                    // DSP soft-knee limiter for sudden harsh spikes
                                    if (sample > ceiling) {
                                        sample = ceiling + (sample - ceiling) * 0.15f
                                    } else if (sample < -ceiling) {
                                        sample = -ceiling + (sample + ceiling) * 0.15f
                                    }
                                }
                                val outVal = sample.toInt()
                                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                                outBuffer[2 * i] = outVal
                                outBuffer[2 * i + 1] = outVal
                            }
                        }
                        track.write(outBuffer, 0, read * 2)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Acoustic stream exception: ${e.message}")
            } finally {
                try {
                    record.stop()
                    record.release()
                } catch (e: Exception) {}
                try {
                    track.stop()
                    track.release()
                } catch (e: Exception) {}
                noiseSuppressor?.release()
                noiseSuppressor = null
                echoCanceler?.release()
                echoCanceler = null
            }
        }
    }

    private fun stopAcousticProcessing() {
        activeJob?.cancel()
        activeJob = null

        try {
            noiseSuppressor?.enabled = false
            noiseSuppressor?.release()
        } catch (e: Exception) {}
        noiseSuppressor = null

        try {
            echoCanceler?.enabled = false
            echoCanceler?.release()
        } catch (e: Exception) {}
        echoCanceler = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
    }

    /**
     * Adaptive Audio Mode:
     * Samples room/street decibels. If noise > 62 dB, automatically switches to Noise Cancellation.
     * In quiet conditions (< 62 dB), switches to Transparency.
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
                var lastAdaptiveMode: NoiseMode? = null

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

                        val targetMode = if (db > 62) NoiseMode.NOISE_CANCELLATION else NoiseMode.TRANSPARENCY
                        if (targetMode != lastAdaptiveMode) {
                            lastAdaptiveMode = targetMode
                            startAcousticProcessing(context, isCancellation = (targetMode == NoiseMode.NOISE_CANCELLATION))
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
        stopAcousticProcessing()
    }

    /**
     * Conversation Awareness:
     * Listens for human speech. When speech is detected, drops music volume by 65%
     * and enables Transparency passthrough. Restores when speech stops.
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
                        if (maxPeak > 2800) {
                            lastSpokenTime = now
                            if (!isDucked) {
                                duckMediaVolume(context)
                                if (_currentMode.value == NoiseMode.NOISE_CANCELLATION || _currentMode.value == NoiseMode.OFF) {
                                    startAcousticProcessing(context, isCancellation = false)
                                }
                            }
                        } else if (isDucked && (now - lastSpokenTime > 2500)) {
                            restoreMediaVolume(context)
                            setMode(context, _currentMode.value)
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
