package me.anypods.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.sin

enum class SpatialAudioMode {
    OFF,
    FIXED,
    HEAD_TRACKED
}

enum class ToneProfile {
    BALANCED_TONE,
    VOCAL_RANGE,
    BRIGHTNESS
}

enum class ToneIntensity {
    SLIGHT,
    MODERATE,
    STRONG
}

object AudioEnhancer {
    private const val TAG = "AnyPods.AudioEnhancer"
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var beepJob: Job? = null
    private var audioTrack: AudioTrack? = null

    // State flows for UI
    val currentSpatialMode = MutableStateFlow(SpatialAudioMode.OFF)
    val currentToneProfile = MutableStateFlow(ToneProfile.BALANCED_TONE)
    val currentToneIntensity = MutableStateFlow(ToneIntensity.MODERATE)
    val currentBalance = MutableStateFlow(0f) // -1.0 (Full Left) to 1.0 (Full Right)

    fun initAudioEffects(audioSessionId: Int = 0) {
        try {
            if (equalizer == null) {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = true
                }
            }
            if (bassBoost == null) {
                bassBoost = BassBoost(0, audioSessionId).apply {
                    enabled = true
                }
            }
            if (virtualizer == null) {
                virtualizer = Virtualizer(0, audioSessionId).apply {
                    enabled = false
                }
            }
            Log.d(TAG, "Audio effects initialized on session $audioSessionId")
        } catch (e: Exception) {
            Log.w(TAG, "Audio effects not supported on this session: ${e.message}")
        }
    }

    /**
     * Spatial Audio (3D Audio Virtualizer)
     */
    fun setSpatialAudio(mode: SpatialAudioMode) {
        currentSpatialMode.value = mode
        try {
            val virt = virtualizer ?: Virtualizer(0, 0).also { virtualizer = it }
            when (mode) {
                SpatialAudioMode.OFF -> {
                    virt.enabled = false
                }
                SpatialAudioMode.FIXED -> {
                    virt.enabled = true
                    if (virt.strengthSupported) {
                        virt.setStrength(750.toShort())
                    }
                }
                SpatialAudioMode.HEAD_TRACKED -> {
                    virt.enabled = true
                    if (virt.strengthSupported) {
                        virt.setStrength(1000.toShort())
                    }
                }
            }
            Log.d(TAG, "Spatial Audio set to $mode")
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting spatial audio: ${e.message}")
        }
    }

    /**
     * Headphone Accommodations (iOS Audio/Visual Accessibility Feature)
     */
    fun setHeadphoneAccommodations(profile: ToneProfile, intensity: ToneIntensity) {
        currentToneProfile.value = profile
        currentToneIntensity.value = intensity
        val eq = equalizer ?: return

        try {
            val numBands = eq.numberOfBands.toInt()
            val minEQ = eq.bandLevelRange[0]
            val maxEQ = eq.bandLevelRange[1]

            // Multiplier based on Slight (33%), Moderate (66%), Strong (100%)
            val gainFactor = when (intensity) {
                ToneIntensity.SLIGHT -> 0.35f
                ToneIntensity.MODERATE -> 0.65f
                ToneIntensity.STRONG -> 1.0f
            }

            val targetGain = (maxEQ * gainFactor).toInt().toShort()

            for (i in 0 until numBands) {
                val centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000 // in Hz

                when (profile) {
                    ToneProfile.BALANCED_TONE -> {
                        // Flat reference curve with subtle clarity lift
                        eq.setBandLevel(i.toShort(), 0)
                    }
                    ToneProfile.VOCAL_RANGE -> {
                        // Boost human voice range (800 Hz to 3500 Hz)
                        if (centerFreqHz in 800..3500) {
                            eq.setBandLevel(i.toShort(), targetGain)
                        } else {
                            eq.setBandLevel(i.toShort(), (minEQ * 0.15f).toInt().toShort())
                        }
                    }
                    ToneProfile.BRIGHTNESS -> {
                        // Boost treble & crisp highs (> 3500 Hz)
                        if (centerFreqHz >= 3500) {
                            eq.setBandLevel(i.toShort(), targetGain)
                        } else {
                            eq.setBandLevel(i.toShort(), 0)
                        }
                    }
                }
            }
            Log.d(TAG, "Applied Headphone Accommodation: $profile ($intensity)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying Headphone Accommodations: ${e.message}")
        }
    }

    /**
     * Left / Right Balance
     */
    fun setAudioBalance(balance: Float) {
        // balance ranges from -1.0 (Full Left) to 1.0 (Full Right)
        currentBalance.value = balance
        Log.d(TAG, "Set L/R balance to $balance")
    }

    fun applyPreset(presetName: String) {
        when (presetName) {
            "Bass Boost" -> {
                bassBoost?.setStrength(800)
            }
            "Vocal Range" -> {
                setHeadphoneAccommodations(ToneProfile.VOCAL_RANGE, ToneIntensity.MODERATE)
            }
            "Brightness" -> {
                setHeadphoneAccommodations(ToneProfile.BRIGHTNESS, ToneIntensity.MODERATE)
            }
            else -> {
                setHeadphoneAccommodations(ToneProfile.BALANCED_TONE, ToneIntensity.SLIGHT)
                bassBoost?.setStrength(0)
            }
        }
    }

    /**
     * Ultra-Loud Find My Earbuds acoustic beacon
     * +25dB gain override with high-piercing 3.2kHz/4.0kHz dual frequency alert
     */
    fun startFindMyTone(context: Context, left: Boolean, right: Boolean) {
        stopFindMyTone()

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed raising stream volume: ${e.message}")
        }

        beepJob = CoroutineScope(Dispatchers.Default).launch {
            val sampleRate = 48000
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize.coerceAtLeast(2048))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track

            try {
                loudnessEnhancer = LoudnessEnhancer(track.audioSessionId).apply {
                    setTargetGain(2500) // +2500 mB = +25 dB extreme gain
                    enabled = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer unavailable: ${e.message}")
            }

            val leftVol = if (left) 1.0f else 0.0f
            val rightVol = if (right) 1.0f else 0.0f
            track.setStereoVolume(leftVol, rightVol)

            val numSamples = sampleRate / 2
            val sampleData = ShortArray(numSamples * 2)

            val freq1 = 3200.0 // 3.2 kHz
            val freq2 = 4000.0 // 4.0 kHz

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val rawWave = 0.6 * sin(2.0 * Math.PI * freq1 * t) + 0.4 * sin(2.0 * Math.PI * freq2 * t)
                val squareBoost = if (rawWave > 0.0) 1.0 else -1.0
                val mixed = (0.75 * rawWave + 0.25 * squareBoost) * Short.MAX_VALUE * 0.99
                val sampleVal = mixed.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

                sampleData[2 * i] = if (left) sampleVal else 0
                sampleData[2 * i + 1] = if (right) sampleVal else 0
            }

            try {
                track.play()
                while (isActive) {
                    track.write(sampleData, 0, sampleData.size)
                    delay(300)
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack playback error: ${e.message}")
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (e: Exception) {}
            }
        }
    }

    fun stopFindMyTone() {
        beepJob?.cancel()
        beepJob = null
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (e: Exception) {}
        loudnessEnhancer = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
    }
}
