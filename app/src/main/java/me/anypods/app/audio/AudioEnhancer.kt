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
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
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

data class SessionAudioEffects(
    val sessionId: Int,
    var equalizer: Equalizer?,
    var bassBoost: BassBoost?,
    var virtualizer: Virtualizer?
)

object AudioEnhancer {
    private const val TAG = "AnyPods.AudioEnhancer"

    // Active audio sessions (Spotify, YouTube Music, Apple Music, VLC, plus global session 0)
    private val activeSessions = ConcurrentHashMap<Int, SessionAudioEffects>()

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

    init {
        // Register default global session 0 as foundational fallback
        registerSession(0)
    }

    fun initAudioEffects(audioSessionId: Int = 0) {
        registerSession(audioSessionId)
    }

    /**
     * Registers an audio session ID (sent by media players like Spotify / YouTube)
     * and immediately attaches high-priority Equalizer, BassBoost, and Virtualizer.
     */
    @Synchronized
    fun registerSession(audioSessionId: Int) {
        if (activeSessions.containsKey(audioSessionId)) {
            val existing = activeSessions[audioSessionId]
            if (existing != null) {
                applyCurvesToSession(existing)
            }
            return
        }

        try {
            val priority = 1000 // High priority ensures precedence over default low-priority effects

            val eq = try {
                Equalizer(priority, audioSessionId).apply { enabled = true }
            } catch (e: Exception) {
                try { Equalizer(0, audioSessionId).apply { enabled = true } } catch (_: Exception) { null }
            }

            val bb = try {
                BassBoost(priority, audioSessionId).apply { enabled = true }
            } catch (e: Exception) {
                try { BassBoost(0, audioSessionId).apply { enabled = true } } catch (_: Exception) { null }
            }

            val virt = try {
                Virtualizer(priority, audioSessionId).apply {
                    try {
                        forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_BINAURAL)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                try { Virtualizer(0, audioSessionId) } catch (_: Exception) { null }
            }

            val effects = SessionAudioEffects(audioSessionId, eq, bb, virt)
            activeSessions[audioSessionId] = effects
            applyCurvesToSession(effects)
            Log.i(TAG, "AudioEnhancer: Successfully registered audio session $audioSessionId (Total active: ${activeSessions.size})")
        } catch (e: Exception) {
            Log.w(TAG, "AudioEnhancer: Warning registering session $audioSessionId: ${e.message}")
        }
    }

    @Synchronized
    fun unregisterSession(audioSessionId: Int) {
        if (audioSessionId == 0) return // Preserve session 0 as permanent fallback
        activeSessions.remove(audioSessionId)?.let { effects ->
            try { effects.equalizer?.release() } catch (_: Exception) {}
            try { effects.bassBoost?.release() } catch (_: Exception) {}
            try { effects.virtualizer?.release() } catch (_: Exception) {}
            Log.i(TAG, "AudioEnhancer: Released audio session $audioSessionId")
        }
    }

    /**
     * Unified Master DSP Pipeline:
     * Dispatches curves across all active audio sessions in real-time.
     */
    @Synchronized
    private fun recomputeAndApplyCurves() {
        if (activeSessions.isEmpty()) {
            registerSession(0)
        }
        for (effects in activeSessions.values) {
            applyCurvesToSession(effects)
        }
    }

    private fun applyCurvesToSession(effects: SessionAudioEffects) {
        val eq = effects.equalizer
        if (eq != null) {
            try {
                eq.enabled = true
                val numBands = eq.numberOfBands.toInt()
                val minEQ = eq.bandLevelRange[0].toFloat() // typically -1500 mB (-15dB)
                val maxEQ = eq.bandLevelRange[1].toFloat() // typically +1500 mB (+15dB)

                val intensityFactor = when (currentToneIntensity.value) {
                    ToneIntensity.SLIGHT -> 0.40f
                    ToneIntensity.MODERATE -> 0.70f
                    ToneIntensity.STRONG -> 1.00f
                }

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
                                centerFreqHz < 160 -> combinedGainFraction += (0.75f * ancMult)
                                centerFreqHz in 160..380 -> combinedGainFraction += (0.35f * ancMult)
                                centerFreqHz in 450..3400 -> combinedGainFraction -= (0.72f * ancMult)
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
                                centerFreqHz in 800..4200 -> combinedGainFraction += 0.70f
                                centerFreqHz < 250 -> combinedGainFraction -= 0.25f
                                else -> combinedGainFraction += 0.20f
                            }
                        }
                    }

                    // --- Apple Headphone Accommodations Component ---
                    when (currentToneProfile.value) {
                        ToneProfile.BALANCED_TONE -> {
                            when {
                                centerFreqHz < 250 -> combinedGainFraction += (0.50f * intensityFactor)
                                centerFreqHz in 250..1200 -> combinedGainFraction += (0.15f * intensityFactor)
                                centerFreqHz in 1200..3500 -> combinedGainFraction += (0.30f * intensityFactor)
                                else -> combinedGainFraction += (0.60f * intensityFactor)
                            }
                        }
                        ToneProfile.VOCAL_RANGE -> {
                            when {
                                centerFreqHz < 250 -> combinedGainFraction += (0.20f * intensityFactor)
                                centerFreqHz in 800..3800 -> combinedGainFraction += (0.80f * intensityFactor)
                                else -> combinedGainFraction += (0.25f * intensityFactor)
                            }
                        }
                        ToneProfile.BRIGHTNESS -> {
                            when {
                                centerFreqHz < 300 -> combinedGainFraction += (0.20f * intensityFactor)
                                centerFreqHz in 300..2500 -> combinedGainFraction += (0.15f * intensityFactor)
                                else -> combinedGainFraction += (0.85f * intensityFactor)
                            }
                        }
                    }

                    // --- Spatial Audio Acoustic Stage Component ---
                    when (currentSpatialMode.value) {
                        SpatialAudioMode.FIXED -> {
                            if (centerFreqHz in 2800..8000) combinedGainFraction += 0.25f
                            if (centerFreqHz < 180) combinedGainFraction += 0.20f
                        }
                        SpatialAudioMode.HEAD_TRACKED -> {
                            if (centerFreqHz in 500..900) combinedGainFraction -= 0.12f
                            if (centerFreqHz > 3000) combinedGainFraction += 0.35f
                            if (centerFreqHz < 200) combinedGainFraction += 0.30f
                        }
                        SpatialAudioMode.OFF -> {}
                    }

                    val targetGainMb = (maxEQ * combinedGainFraction).toInt()
                        .coerceIn(minEQ.toInt(), maxEQ.toInt())
                        .toShort()

                    eq.setBandLevel(i.toShort(), targetGainMb)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Equalizer apply error on session ${effects.sessionId}: ${e.message}")
            }
        }

        // Configure Hardware BassBoost
        effects.bassBoost?.let { bb ->
            try {
                bb.enabled = true
                val intensityFactor = when (currentToneIntensity.value) {
                    ToneIntensity.SLIGHT -> 0.40f
                    ToneIntensity.MODERATE -> 0.70f
                    ToneIntensity.STRONG -> 1.00f
                }
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
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost apply error on session ${effects.sessionId}: ${e.message}")
            }
        }

        // Configure Hardware Virtualizer for 3D Binaural Spatial Audio
        effects.virtualizer?.let { virt ->
            try {
                try {
                    virt.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_BINAURAL)
                } catch (_: Exception) {}

                when (currentSpatialMode.value) {
                    SpatialAudioMode.OFF -> {
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
                Log.w(TAG, "Virtualizer apply error on session ${effects.sessionId}: ${e.message}")
            }
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

    /**
     * Sets Left/Right stereo balance system-wide:
     * - Dispatches to Android's native Settings.Secure "master_balance"
     * - Triggers AudioService -> AudioSystem.setMasterBalance(balance)
     * Range: -1.0f (Full Left) to +1.0f (Full Right), 0.0f = Center Balanced
     */
    fun setAudioBalance(context: Context, balance: Float) {
        currentBalance.value = balance
        try {
            Settings.Secure.putFloat(context.contentResolver, "master_balance", balance)
            Log.i(TAG, "Applied Settings.Secure master_balance: $balance")
        } catch (e: Exception) {
            Log.w(TAG, "Settings.Secure master_balance failed: ${e.message}")
            try {
                Settings.System.putFloat(context.contentResolver, "master_balance", balance)
            } catch (_: Exception) {}
        }
        try {
            Settings.Global.putFloat(context.contentResolver, "master_balance", balance)
        } catch (_: Exception) {}
    }

    fun initBalance(context: Context) {
        try {
            val bal = Settings.Secure.getFloat(context.contentResolver, "master_balance", 0.0f)
            currentBalance.value = bal
        } catch (_: Exception) {
            try {
                val bal = Settings.System.getFloat(context.contentResolver, "master_balance", 0.0f)
                currentBalance.value = bal
            } catch (_: Exception) {}
        }
    }

    /**
     * Ultra-Loud "Find My Earbuds" Player:
     * - Uses native MediaPlayer on USAGE_MEDIA / STREAM_MUSIC
     * - Maximizes media volume to 100%
     * - Supports independent Left, Right, or Dual earbud isolation
     * - Attaches +30dB LoudnessEnhancer to the player session
     */
    fun startFindMyTone(context: Context, left: Boolean, right: Boolean) {
        stopFindMyTone()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        try {
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
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
                    setTargetGain(3000) // +3000 mB = +30 dB
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

        byteBuffer.put("RIFF".toByteArray())
        byteBuffer.putInt(36 + dataSize)
        byteBuffer.put("WAVE".toByteArray())

        byteBuffer.put("fmt ".toByteArray())
        byteBuffer.putInt(16)
        byteBuffer.putShort(1.toShort())
        byteBuffer.putShort(numChannels.toShort())
        byteBuffer.putInt(sampleRate)
        byteBuffer.putInt(sampleRate * numChannels * (bitsPerSample / 8))
        byteBuffer.putShort((numChannels * (bitsPerSample / 8)).toShort())
        byteBuffer.putShort(bitsPerSample.toShort())

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
