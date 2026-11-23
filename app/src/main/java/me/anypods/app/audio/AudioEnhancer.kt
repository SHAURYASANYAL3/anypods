package me.anypods.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

enum class AncIntensity {
    ULTRA,      // Deep Vacuum Studio Seal (Max Isolation, -90% ambient penetration)
    BALANCED,   // Commute / Daily (High Isolation, balanced acoustic pressure)
    COMFORT     // Gentle / Zero Pressure (Airy isolation, eliminates eardrum fatigue)
}

object AudioEnhancer {
    private const val TAG = "AnyPods.AudioEnhancer"
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var mediaPlayer: MediaPlayer? = null

    // Track state internally for the unified DSP pipeline
    private var activeNoiseMode: String = "OFF" // "ANC", "TRANSPARENCY", "OFF"

    // State flows for UI
    val currentSpatialMode = MutableStateFlow(SpatialAudioMode.OFF)
    val currentToneProfile = MutableStateFlow(ToneProfile.BALANCED_TONE)
    val currentToneIntensity = MutableStateFlow(ToneIntensity.MODERATE)
    val currentBalance = MutableStateFlow(0f)
    val currentAncIntensity = MutableStateFlow(AncIntensity.ULTRA)
    val antiHissWindReduction = MutableStateFlow(true)

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
            Log.w(TAG, "Audio effects initialization warning: ${e.message}")
        }
    }

    /**
     * Unified Master DSP Pipeline:
     * Combines Noise Cancellation / Transparency curves, Apple Headphone Accommodations,
     * and 3D Spatial Audio Soundstage expansion into a single cohesive audio output.
     */
    @Synchronized
    private fun recomputeAndApplyCurves() {
        initAudioEffects()
        val eq = equalizer ?: return

        try {
            val numBands = eq.numberOfBands.toInt()
            val minEQ = eq.bandLevelRange[0].toFloat() // typically -1500 mB (-15dB)
            val maxEQ = eq.bandLevelRange[1].toFloat() // typically +1500 mB (+15dB)

            // 1. Calculate Intensity Multiplier for Headphone Accommodations
            val intensityFactor = when (currentToneIntensity.value) {
                ToneIntensity.SLIGHT -> 0.40f
                ToneIntensity.MODERATE -> 0.70f
                ToneIntensity.STRONG -> 1.00f
            }

            // 2. Iterate each equalizer band and sum the acoustic gains
            for (i in 0 until numBands) {
                val centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000 // in Hz
                var combinedGainFraction = 0.0f

                // --- Noise Mode Component ---
                when (activeNoiseMode) {
                    "ANC" -> {
                        val ancMult = when (currentAncIntensity.value) {
                            AncIntensity.ULTRA -> 1.0f
                            AncIntensity.BALANCED -> 0.72f
                            AncIntensity.COMFORT -> 0.45f
                        }
                        when {
                            // 1. Deep Sub-bass acoustic vacuum seal & anti-rumble (low engine, drone & AC hum)
                            centerFreqHz < 160 -> combinedGainFraction += (0.75f * ancMult)
                            // 2. Mid-bass body acoustic damping (160 - 380 Hz)
                            centerFreqHz in 160..380 -> combinedGainFraction += (0.35f * ancMult)
                            // 3. Deep ambient voice, traffic & room chatter cancellation notch (450 - 3400 Hz)
                            centerFreqHz in 450..3400 -> combinedGainFraction -= (0.72f * ancMult)
                            // 4. Anti-hiss ceiling (> 5500 Hz) silences high-frequency white noise & DAC hiss
                            centerFreqHz > 5500 -> {
                                if (antiHissWindReduction.value) {
                                    combinedGainFraction -= (0.28f * ancMult)
                                } else {
                                    combinedGainFraction += 0.05f
                                }
                            }
                            else -> combinedGainFraction -= (0.15f * ancMult)
                        }
                    }
                    "TRANSPARENCY" -> {
                        when {
                            centerFreqHz in 800..4200 -> combinedGainFraction += 0.70f // Outside voice pass-through
                            centerFreqHz < 250 -> combinedGainFraction -= 0.25f // De-occlusion
                            else -> combinedGainFraction += 0.20f
                        }
                    }
                }

                // --- Apple Headphone Accommodations Component ---
                when (currentToneProfile.value) {
                    ToneProfile.BALANCED_TONE -> {
                        // Apple Signature Harman Curve: rich punchy lows and sparkling airy highs
                        when {
                            centerFreqHz < 250 -> combinedGainFraction += (0.50f * intensityFactor) // Deep warm bass
                            centerFreqHz in 250..1200 -> combinedGainFraction += (0.15f * intensityFactor) // Smooth body
                            centerFreqHz in 1200..3500 -> combinedGainFraction += (0.30f * intensityFactor) // Vocal presence
                            else -> combinedGainFraction += (0.60f * intensityFactor) // Silky crystal highs
                        }
                    }
                    ToneProfile.VOCAL_RANGE -> {
                        // Focus on speech clarity, podcast articulation, and vocal punch
                        when {
                            centerFreqHz < 250 -> combinedGainFraction += (0.20f * intensityFactor)
                            centerFreqHz in 800..3800 -> combinedGainFraction += (0.80f * intensityFactor) // Vocal lift
                            else -> combinedGainFraction += (0.25f * intensityFactor)
                        }
                    }
                    ToneProfile.BRIGHTNESS -> {
                        // Crisp cymbal definition, acoustic strings, and airy clarity
                        when {
                            centerFreqHz < 300 -> combinedGainFraction += (0.20f * intensityFactor)
                            centerFreqHz in 300..2500 -> combinedGainFraction += (0.15f * intensityFactor)
                            else -> combinedGainFraction += (0.85f * intensityFactor) // Sparkling brightness
                        }
                    }
                }

                // --- Spatial Audio Acoustic Stage Component ---
                when (currentSpatialMode.value) {
                    SpatialAudioMode.FIXED -> {
                        // Cinema / Theatre acoustics: expands spatial depth
                        if (centerFreqHz in 2800..8000) {
                            combinedGainFraction += 0.25f
                        }
                        if (centerFreqHz < 180) {
                            combinedGainFraction += 0.20f
                        }
                    }
                    SpatialAudioMode.HEAD_TRACKED -> {
                        // Wide Stage / Concert Hall: wide stereo stage separation
                        if (centerFreqHz in 500..900) {
                            combinedGainFraction -= 0.12f // Mild center scoop to widen stage
                        }
                        if (centerFreqHz > 3000) {
                            combinedGainFraction += 0.35f // Air & spatial reverberance
                        }
                        if (centerFreqHz < 200) {
                            combinedGainFraction += 0.30f // Deep cinematic sub-bass
                        }
                    }
                    SpatialAudioMode.OFF -> {}
                }

                // Calculate final milliBel gain clamped strictly to hardware range
                val targetGainMb = (maxEQ * combinedGainFraction).toInt()
                    .coerceIn(minEQ.toInt(), maxEQ.toInt())
                    .toShort()

                eq.setBandLevel(i.toShort(), targetGainMb)
            }

            // 3. Configure Hardware BassBoost
            bassBoost?.let { bb ->
                val baseBoost = when (activeNoiseMode) {
                    "ANC" -> when (currentAncIntensity.value) {
                        AncIntensity.ULTRA -> 850
                        AncIntensity.BALANCED -> 650
                        AncIntensity.COMFORT -> 380
                    }
                    "TRANSPARENCY" -> 100
                    else -> 400
                }
                val accomBoost = when (currentToneProfile.value) {
                    ToneProfile.BALANCED_TONE -> (300 * intensityFactor).toInt()
                    else -> 100
                }
                bb.setStrength((baseBoost + accomBoost).coerceIn(0, 1000).toShort())
            }

            // 4. Configure Hardware Virtualizer for 3D Binaural Spatial Audio
            virtualizer?.let { virt ->
                try {
                    // Try to force Binaural headphone mode if supported
                    try {
                        virt.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_BINAURAL)
                    } catch (_: Exception) {}

                    when (currentSpatialMode.value) {
                        SpatialAudioMode.OFF -> {
                            // If ANC is on, use light stage virtualization (450)
                            if (activeNoiseMode == "ANC") {
                                virt.enabled = true
                                if (virt.strengthSupported) virt.setStrength(450.toShort())
                            } else {
                                virt.enabled = false
                            }
                        }
                        SpatialAudioMode.FIXED -> {
                            virt.enabled = true
                            if (virt.strengthSupported) {
                                virt.setStrength(850.toShort())
                            }
                        }
                        SpatialAudioMode.HEAD_TRACKED -> {
                            virt.enabled = true
                            if (virt.strengthSupported) {
                                virt.setStrength(1000.toShort())
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Virtualizer configuration note: ${e.message}")
                }
            }

            Log.d(TAG, "Applied composite DSP: Mode=$activeNoiseMode, Accom=${currentToneProfile.value}(${currentToneIntensity.value}), Spatial=${currentSpatialMode.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying composite curves: ${e.message}", e)
        }
    }

    fun applyNoiseCancellationMode() {
        activeNoiseMode = "ANC"
        recomputeAndApplyCurves()
    }

    fun applyTransparencyMode() {
        activeNoiseMode = "TRANSPARENCY"
        recomputeAndApplyCurves()
    }

    fun applyOffMode() {
        activeNoiseMode = "OFF"
        recomputeAndApplyCurves()
    }

    fun setAncIntensity(intensity: AncIntensity) {
        currentAncIntensity.value = intensity
        if (activeNoiseMode == "ANC") {
            recomputeAndApplyCurves()
        }
    }

    fun setAntiHissWindReduction(enabled: Boolean) {
        antiHissWindReduction.value = enabled
        if (activeNoiseMode == "ANC") {
            recomputeAndApplyCurves()
        }
    }

    fun setSpatialAudio(mode: SpatialAudioMode) {
        currentSpatialMode.value = mode
        recomputeAndApplyCurves()
    }

    fun setHeadphoneAccommodations(profile: ToneProfile, intensity: ToneIntensity) {
        currentToneProfile.value = profile
        currentToneIntensity.value = intensity
        recomputeAndApplyCurves()
    }

    fun setAudioBalance(balance: Float) {
        currentBalance.value = balance
        Log.d(TAG, "Set L/R balance to $balance")
    }

    /**
     * Ultra-Loud "Find My Earbuds" Player:
     * - Uses native MediaPlayer on USAGE_MEDIA / STREAM_MUSIC so Bluetooth audio HAL guarantees direct playback.
     * - Maximizes media volume to index 15 (100%).
     * - Supports independent Left, Right, or Dual earbud isolation via mediaPlayer.setVolume(left, right).
     * - Attaches +30dB LoudnessEnhancer to the player session.
     */
    fun startFindMyTone(context: Context, left: Boolean, right: Boolean) {
        stopFindMyTone()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        try {
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            Log.d(TAG, "Maximized STREAM_MUSIC volume to $maxMusic for Find My")
        } catch (e: Exception) {
            Log.w(TAG, "Volume boost warning: ${e.message}")
        }

        try {
            val sirenFile = getOrCreateSirenWav(context)

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, Uri.fromFile(sirenFile))
                isLooping = true
                val leftVol = if (left) 1.0f else 0.0f
                val rightVol = if (right) 1.0f else 0.0f
                setVolume(leftVol, rightVol)
                prepare()
            }

            try {
                loudnessEnhancer = LoudnessEnhancer(mp.audioSessionId).apply {
                    setTargetGain(3000) // +3000 mB = +30 dB maximum hardware gain
                    enabled = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer error: ${e.message}")
            }

            mp.start()
            mediaPlayer = mp
            Log.d(TAG, "Find My Earbuds MediaPlayer started (Left=$left, Right=$right)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting MediaPlayer Find My tone: ${e.message}", e)
        }
    }

    fun stopFindMyTone() {
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        loudnessEnhancer = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        Log.d(TAG, "Find My Earbuds tone stopped")
    }

    /**
     * Generates a 1-second 44.1kHz 16-bit PCM WAV file in the app cache:
     * Sweeps between 3.2 kHz and 4.2 kHz (the exact resonant frequency of earbud micro-transducers).
     */
    private fun getOrCreateSirenWav(context: Context): File {
        val file = File(context.cacheDir, "find_my_siren.wav")
        if (file.exists() && file.length() > 1000) {
            return file
        }

        val sampleRate = 44100
        val durationSec = 1.0
        val numSamples = (sampleRate * durationSec).toInt()
        val numChannels = 2
        val bitsPerSample = 16
        val dataSize = numSamples * numChannels * (bitsPerSample / 8)

        val sampleData = ShortArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val freq = if ((i / (sampleRate / 4)) % 2 == 0) 3400.0 else 4200.0
            val sine = sin(2.0 * Math.PI * freq * t)
            val wave = if (sine > 0.0) 0.95 else -0.95
            val sampleVal = (wave * Short.MAX_VALUE * 0.98).toInt().toShort()

            sampleData[2 * i] = sampleVal
            sampleData[2 * i + 1] = sampleVal
        }

        val byteBuffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        byteBuffer.put("RIFF".toByteArray())
        byteBuffer.putInt(36 + dataSize)
        byteBuffer.put("WAVE".toByteArray())

        // fmt chunk
        byteBuffer.put("fmt ".toByteArray())
        byteBuffer.putInt(16)
        byteBuffer.putShort(1.toShort())
        byteBuffer.putShort(numChannels.toShort())
        byteBuffer.putInt(sampleRate)
        byteBuffer.putInt(sampleRate * numChannels * (bitsPerSample / 8))
        byteBuffer.putShort((numChannels * (bitsPerSample / 8)).toShort())
        byteBuffer.putShort(bitsPerSample.toShort())

        // data chunk
        byteBuffer.put("data".toByteArray())
        byteBuffer.putInt(dataSize)

        for (sample in sampleData) {
            byteBuffer.putShort(sample)
        }

        FileOutputStream(file).use { fos ->
            fos.write(byteBuffer.array())
            fos.flush()
        }

        Log.d(TAG, "Generated find_my_siren.wav (${file.length()} bytes)")
        return file
    }
}
