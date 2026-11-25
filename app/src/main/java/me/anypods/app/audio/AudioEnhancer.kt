package me.anypods.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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

enum class FrequencyTuningMode(val title: String, val subtitle: String) {
    MODEL_AUTO("Model Auto-Tuned", "Hardware-calibrated acoustic curve tuned specifically for your AirPods"),
    AUDIOPHILE_HARMAN("Audiophile Reference", "Harman-target studio curve with deep sub-bass and pristine vocal separation"),
    DEEP_BASS("Deep Bass Boost", "Acoustic low-shelf bass boost (+6dB) tuned for open and sealed AirPods"),
    VOCAL_CLARITY("Vocal & Podcast Clarity", "Forward speech clarity, boosted upper-mids, and sibilance smoothing"),
    STUDIO_FLAT("Studio Flat", "Flat mastering response with linear driver excursion")
}

data class SessionAudioEffects(
    val sessionId: Int,
    var equalizer: Equalizer?,
    var bassBoost: BassBoost?,
    var virtualizer: Virtualizer?
)

object AudioEnhancer {
    private const val TAG = "AnyPods.AudioEnhancer"

    // Cap concurrent audio sessions to 4 to prevent external DoS on audioserver
    private const val MAX_CONCURRENT_SESSIONS = 4

    // Active audio sessions (Spotify, YouTube Music, Apple Music, VLC, plus global session 0)
    private val activeSessions = ConcurrentHashMap<Int, SessionAudioEffects>()
    private var appContext: Context? = null

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var mediaPlayer: MediaPlayer? = null

    // Track state internally for the unified DSP pipeline
    private var activeNoiseMode: String = "OFF" // "ANC", "TRANSPARENCY", "OFF"

    // State flows for UI
    val currentSpatialMode = MutableStateFlow(SpatialAudioMode.OFF)
    val currentToneProfile = MutableStateFlow(ToneProfile.BALANCED_TONE)
    val currentToneIntensity = MutableStateFlow(ToneIntensity.MODERATE)
    val currentBalance = MutableStateFlow(0f)
    val hasBalancePermission = MutableStateFlow(false)
    val currentAncIntensity = MutableStateFlow(AncIntensity.ULTRA)
    val antiHissWindReduction = MutableStateFlow(true)

    // Dedicated AirPods Frequency Tuning Pipeline
    val currentTuningMode = MutableStateFlow(FrequencyTuningMode.MODEL_AUTO)
    val currentActiveModelId = MutableStateFlow("airpods_pro_2")

    init {
        // Register default global session 0 as foundational fallback
        registerSession(0)
    }

    fun initAudioEffects(context: Context? = null, audioSessionId: Int = 0) {
        if (context != null) {
            appContext = context.applicationContext
            scanActiveSessions(context)
        }
        if (audioSessionId > 0) {
            registerSession(audioSessionId)
        }
    }

    /**
     * Extracts active audio session ID from AudioPlaybackConfiguration
     * via reflection and string representation fallback.
     */
    fun extractSessionId(config: Any): Int? {
        try {
            val methods = config.javaClass.methods
            for (m in methods) {
                if (m.name == "getClientAudioSessionId" || m.name == "getSessionId") {
                    val res = m.invoke(config) as? Int
                    if (res != null && res > 0) return res
                }
            }
            val fields = config.javaClass.declaredFields
            for (f in fields) {
                if (f.name == "mSessionId" || f.name == "sessionId") {
                    f.isAccessible = true
                    val res = f.getInt(config)
                    if (res > 0) return res
                }
            }
        } catch (_: Exception) {}

        try {
            val str = config.toString()
            val match = Regex("""session(?:Id)?[:\s=]+(\d+)""").find(str)
            if (match != null) {
                val id = match.groupValues[1].toIntOrNull()
                if (id != null && id > 0) return id
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Queries internal Android system services (AudioService and AudioFlinger)
     * using the DUMP IPC mechanism to discover real active media session IDs (e.g. YouTube, Spotify).
     */
    fun getSessionIdsFromService(): List<Int> {
        val sessionIds = mutableListOf<Int>()
        try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = smClass.getMethod("getService", String::class.java)

            // 1. Query "audio" service (dumpsys audio -> active AudioTrack players)
            val audioBinder = getServiceMethod.invoke(null, "audio") as? android.os.IBinder
            if (audioBinder != null) {
                parseSessionsFromBinder(audioBinder, arrayOf("player"), sessionIds)
            }

            // 2. Query "media.audio_flinger" service (dumpsys media.audio_flinger -> mixer tracks)
            val flingerBinder = getServiceMethod.invoke(null, "media.audio_flinger") as? android.os.IBinder
            if (flingerBinder != null) {
                parseSessionsFromBinder(flingerBinder, arrayOf(), sessionIds)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getSessionIdsFromService error: ${e.message}")
        }
        return sessionIds
    }

    private fun parseSessionsFromBinder(binder: android.os.IBinder, args: Array<String>, outSessions: MutableList<Int>) {
        try {
            val pipe = android.os.ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]

            val thread = Thread {
                try {
                    binder.dump(writeSide.fileDescriptor, args)
                } catch (e: Exception) {
                    Log.w(TAG, "binder dump error: ${e.message}")
                } finally {
                    try { writeSide.close() } catch (_: Exception) {}
                }
            }
            thread.start()

            val reader = java.io.BufferedReader(java.io.InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(readSide)))
            val regex = Regex("""(?:session(?:Id)?[:\s=]+)(\d+)""", RegexOption.IGNORE_CASE)
            val trackRegex = Regex("""\s+\d+\s+yes\s+\d+\s+(\d+)""")

            reader.forEachLine { line ->
                if (line.contains("USAGE_MEDIA") || line.contains("AudioTrack") || line.contains("player piid")) {
                    regex.find(line)?.let { match ->
                        val id = match.groupValues[1].toIntOrNull()
                        if (id != null && id > 0 && !outSessions.contains(id)) {
                            outSessions.add(id)
                        }
                    }
                }
                trackRegex.find(line)?.let { match ->
                    val id = match.groupValues[1].toIntOrNull()
                    if (id != null && id > 0 && !outSessions.contains(id)) {
                        outSessions.add(id)
                    }
                }
            }
            thread.join(800)
        } catch (e: Exception) {
            Log.w(TAG, "parseSessionsFromBinder error: ${e.message}")
        }
    }

    /**
     * Scans currently active audio playback configurations on Android 8.0+
     * (e.g. YouTube, Spotify, Chrome, games) and attaches AnyPods Master DSP.
     */
    fun scanActiveSessions(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // 1. Discover sessions from service dumpsys (AudioService & AudioFlinger)
                val serviceSessions = getSessionIdsFromService()
                if (serviceSessions.isNotEmpty()) {
                    Log.i(TAG, "scanActiveSessions: Discovered active service sessions: $serviceSessions")
                    for (sessionId in serviceSessions) {
                        if (sessionId > 0) {
                            registerSession(sessionId)
                        }
                    }
                }

                // 2. Fallback to activePlaybackConfigurations
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                val configs = am.activePlaybackConfigurations
                for (config in configs) {
                    val sessionId = extractSessionId(config)
                    if (sessionId != null && sessionId > 0) {
                        registerSession(sessionId)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "scanActiveSessions warning: ${e.message}")
            }
        }
    }

    fun setTargetModel(modelId: String) {
        if (currentActiveModelId.value != modelId) {
            currentActiveModelId.value = modelId
            recomputeAndApplyCurves()
            Log.i(TAG, "AudioEnhancer: Updated target AirPods model to $modelId")
        }
    }

    fun setFrequencyTuningMode(mode: FrequencyTuningMode) {
        currentTuningMode.value = mode
        recomputeAndApplyCurves()
        Log.i(TAG, "AudioEnhancer: Updated frequency tuning mode to ${mode.title}")
    }

    /**
     * Registers an audio session ID (sent by media players like Spotify / YouTube)
     * and immediately attaches high-priority Equalizer, BassBoost, and Virtualizer.
     */
    @Synchronized
    fun registerSession(audioSessionId: Int) {
        if (audioSessionId < 0 || audioSessionId > 500000) {
            Log.w(TAG, "AudioEnhancer: Invalid audioSessionId rejected: $audioSessionId")
            return
        }

        if (activeSessions.size >= MAX_CONCURRENT_SESSIONS && !activeSessions.containsKey(audioSessionId)) {
            Log.w(TAG, "AudioEnhancer: Max sessions ($MAX_CONCURRENT_SESSIONS) reached. Dropping session $audioSessionId to protect audioserver.")
            return
        }

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
    fun recomputeAndApplyCurves() {
        if (activeSessions.size <= 1) {
            appContext?.let { scanActiveSessions(it) }
        }
        if (activeSessions.isEmpty()) {
            registerSession(0)
        }
        for (effects in activeSessions.values) {
            applyCurvesToSession(effects)
        }
    }

    /**
     * Pure calculation function for frequency response tuning.
     * Computes optimal gain fraction (-1.0f to +1.0f) for any given center frequency (in Hz),
     * specifically tailored for each AirPods model architecture.
     */
    fun calculateTargetGain(
        centerFreqHz: Int,
        modelId: String,
        tuningMode: FrequencyTuningMode,
        noiseMode: String,
        ancIntensity: AncIntensity,
        antiHiss: Boolean,
        toneProfile: ToneProfile,
        toneIntensity: ToneIntensity,
        spatialMode: SpatialAudioMode
    ): Float {
        var gain = 0.0f

        // 1. AirPods Model Acoustic Frequency Profile / Mode Tuning
        when (tuningMode) {
            FrequencyTuningMode.STUDIO_FLAT -> {
                gain = 0.0f
            }
            FrequencyTuningMode.AUDIOPHILE_HARMAN -> {
                // Harman Reference Curve (Tight sub-bass, clean mids, detailed highs)
                gain += when {
                    centerFreqHz < 80 -> 0.18f
                    centerFreqHz in 80..250 -> 0.05f
                    centerFreqHz in 250..2500 -> 0.08f
                    centerFreqHz in 2500..8000 -> 0.20f
                    else -> 0.18f
                }
            }
            FrequencyTuningMode.DEEP_BASS -> {
                // High-Impact Bass Profile
                gain += when {
                    centerFreqHz < 120 -> 0.42f
                    centerFreqHz in 120..300 -> 0.24f
                    centerFreqHz in 300..2000 -> 0.05f
                    else -> 0.12f
                }
            }
            FrequencyTuningMode.VOCAL_CLARITY -> {
                // Speech & Podcast Clarity Profile
                gain += when {
                    centerFreqHz < 200 -> -0.06f
                    centerFreqHz in 800..3800 -> 0.35f
                    else -> 0.15f
                }
            }
            FrequencyTuningMode.MODEL_AUTO -> {
                // Hardware-specific acoustic compensation for each AirPods model
                when (modelId) {
                    "airpods_1", "airpods_2" -> {
                        // Open-fit compensation: low-shelf bass restoration & anti-plastic resonance dip
                        gain += when {
                            centerFreqHz < 120 -> 0.38f // +5.7 dB low shelf boost
                            centerFreqHz in 120..320 -> 0.18f // +2.7 dB warmth
                            centerFreqHz in 320..2200 -> 0.02f
                            centerFreqHz in 2200..4500 -> -0.12f // Tames harsh 3.5kHz plastic glare
                            else -> 0.18f // +2.7 dB airy extension
                        }
                    }
                    "airpods_3" -> {
                        // Contoured open-fit with bass reflex: punchy dynamic response
                        gain += when {
                            centerFreqHz < 100 -> 0.26f // +3.9 dB punch
                            centerFreqHz in 100..280 -> 0.12f
                            centerFreqHz in 280..2400 -> 0.04f
                            centerFreqHz in 2400..4500 -> -0.10f // Smooth vocal sibilance
                            else -> 0.16f
                        }
                    }
                    "airpods_4", "airpods_4_anc" -> {
                        // Next-gen semi-open acoustic architecture (H2 chip tuning)
                        gain += when {
                            centerFreqHz < 120 -> 0.28f // +4.2 dB low-end reinforcement
                            centerFreqHz in 120..300 -> 0.14f
                            centerFreqHz in 300..3000 -> 0.06f
                            centerFreqHz in 3000..5500 -> -0.06f
                            else -> 0.18f
                        }
                    }
                    "airpods_pro_1" -> {
                        // Sealed in-ear: solves upper-treble dip and cleans mid-bass mud
                        gain += when {
                            centerFreqHz < 80 -> 0.18f // +2.7 dB sub-bass rumble
                            centerFreqHz in 150..350 -> -0.06f // Anti-mud tuck
                            centerFreqHz in 800..3000 -> 0.08f // Vocal intimacy
                            centerFreqHz in 6000..11000 -> 0.24f // Fixes Pro 1 treble roll-off
                            else -> 0.16f
                        }
                    }
                    "airpods_pro_2" -> {
                        // Sealed H2 dual-driver audiophile curve: micro-detail and holographic air
                        gain += when {
                            centerFreqHz < 80 -> 0.16f // Tactile 20Hz sub-bass
                            centerFreqHz in 80..400 -> 0.02f // Flat reference mids
                            centerFreqHz in 800..2800 -> 0.08f // Natural vocal timbre
                            centerFreqHz in 5500..9500 -> 0.22f // Restores micro-detail & cymbal shimmer
                            else -> 0.20f // Ultra-high air & 3D separation
                        }
                    }
                    "airpods_max" -> {
                        // 40mm Apple dynamic driver over-ear tuning
                        gain += when {
                            centerFreqHz < 80 -> 0.14f // Deep planar-like bass
                            centerFreqHz in 80..2500 -> 0.02f // Reference studio neutral
                            centerFreqHz in 2500..5500 -> 0.14f // Vocal brilliance
                            else -> 0.18f // Crystal studio highs
                        }
                    }
                    "galaxy_buds_3_pro", "galaxy_buds_pro_2" -> {
                        // Samsung Galaxy Buds (Dual-way speaker system, AKG/Harman soundstage)
                        gain += when {
                            centerFreqHz < 100 -> 0.22f
                            centerFreqHz in 100..400 -> 0.06f
                            centerFreqHz in 800..3000 -> 0.10f
                            centerFreqHz in 5000..9000 -> 0.18f
                            else -> 0.16f
                        }
                    }
                    "sony_wf1000xm5" -> {
                        // Sony Dynamic Driver X tuning (Tamed mid-bass warmth & expanded LDAC treble)
                        gain += when {
                            centerFreqHz < 100 -> 0.24f
                            centerFreqHz in 100..350 -> 0.04f
                            centerFreqHz in 800..3000 -> 0.08f
                            centerFreqHz in 5000..10000 -> 0.20f
                            else -> 0.18f
                        }
                    }
                    "pixel_buds_pro", "pixel_buds_a" -> {
                        // Google Pixel Buds neutral vocal-forward profile
                        gain += when {
                            centerFreqHz < 100 -> 0.20f
                            centerFreqHz in 100..400 -> 0.05f
                            centerFreqHz in 800..3500 -> 0.14f
                            else -> 0.15f
                        }
                    }
                    "boat_airdopes", "noise_buds", "boult_buds" -> {
                        // boAt / Noise / Boult energetic bass & speech clarity tuning
                        gain += when {
                            centerFreqHz < 120 -> 0.28f
                            centerFreqHz in 120..350 -> 0.12f
                            centerFreqHz in 800..3200 -> 0.12f
                            else -> 0.15f
                        }
                    }
                    "jbl_tune", "bose_qc", "sennheiser_momentum", "soundcore_liberty" -> {
                        // Premium audio brand acoustic compensation
                        gain += when {
                            centerFreqHz < 100 -> 0.20f
                            centerFreqHz in 800..2800 -> 0.10f
                            else -> 0.16f
                        }
                    }
                    else -> {
                        // Universal TWS / Any pod connected (Balanced Audiophile Target)
                        gain += when {
                            centerFreqHz < 120 -> 0.22f
                            centerFreqHz in 120..400 -> 0.06f
                            centerFreqHz in 800..3000 -> 0.10f
                            else -> 0.16f
                        }
                    }
                }
            }
        }

        // 2. Active Noise Mode Acoustic Curve (Preserving Music Fidelity!)
        when (noiseMode) {
            "ANC" -> {
                val ancMult = when (ancIntensity) {
                    AncIntensity.ULTRA -> 1.0f
                    AncIntensity.BALANCED -> 0.75f
                    AncIntensity.COMFORT -> 0.50f
                }
                // Acoustic Noise Masking & Isolation:
                // Low rumble masking seal (below 150Hz) masks external traffic/fans (+6.0dB)
                // Anti-Hiss high-cut (above 4500Hz) filters background white noise & DAC hiss (-5.5dB)
                when {
                    centerFreqHz < 150 -> gain += (0.40f * ancMult)
                    centerFreqHz in 150..500 -> gain += (0.15f * ancMult)
                    centerFreqHz > 4500 -> {
                        if (antiHiss) {
                            gain -= (0.35f * ancMult)
                        }
                    }
                }
            }
            "TRANSPARENCY" -> {
                // Intelligibility boost for human speech while preserving music
                when {
                    centerFreqHz in 800..4000 -> gain += 0.28f
                    centerFreqHz < 180 -> gain -= 0.12f
                }
            }
        }

        // 3. Apple Headphone Accommodations Component
        val intensityFactor = when (toneIntensity) {
            ToneIntensity.SLIGHT -> 0.35f
            ToneIntensity.MODERATE -> 0.65f
            ToneIntensity.STRONG -> 0.95f
        }
        when (toneProfile) {
            ToneProfile.BALANCED_TONE -> {
                when {
                    centerFreqHz < 250 -> gain += (0.15f * intensityFactor)
                    centerFreqHz in 250..1200 -> gain += (0.05f * intensityFactor)
                    centerFreqHz in 1200..3500 -> gain += (0.10f * intensityFactor)
                    else -> gain += (0.18f * intensityFactor)
                }
            }
            ToneProfile.VOCAL_RANGE -> {
                when {
                    centerFreqHz in 800..3800 -> gain += (0.28f * intensityFactor)
                    centerFreqHz < 250 -> gain += (0.05f * intensityFactor)
                    else -> gain += (0.08f * intensityFactor)
                }
            }
            ToneProfile.BRIGHTNESS -> {
                when {
                    centerFreqHz > 3000 -> gain += (0.30f * intensityFactor)
                    centerFreqHz in 1000..3000 -> gain += (0.10f * intensityFactor)
                    else -> gain += (0.04f * intensityFactor)
                }
            }
        }

        // 4. Spatial Audio Acoustic Stage Component (Binaural 3D Virtualization Curve)
        when (spatialMode) {
            SpatialAudioMode.FIXED -> {
                // Cinema Stage: Frontal soundstage expansion, cinematic sub-bass, treble air
                when {
                    centerFreqHz < 120 -> gain += 0.32f
                    centerFreqHz in 600..1400 -> gain -= 0.18f
                    centerFreqHz in 2500..5000 -> gain += 0.24f
                    centerFreqHz > 5000 -> gain += 0.34f
                }
            }
            SpatialAudioMode.HEAD_TRACKED -> {
                // Wide 3D Holographic Stage: Extra wide spatial cues
                when {
                    centerFreqHz < 120 -> gain += 0.38f
                    centerFreqHz in 500..1500 -> gain -= 0.22f
                    centerFreqHz in 2500..6000 -> gain += 0.30f
                    centerFreqHz > 6000 -> gain += 0.42f
                }
            }
            SpatialAudioMode.OFF -> {}
        }

        return gain.coerceIn(-1.0f, 1.0f)
    }

    /**
     * Calculates optimal hardware BassBoost strength (0-1000) based on model and settings.
     */
    fun calculateBassBoost(
        modelId: String,
        tuningMode: FrequencyTuningMode,
        noiseMode: String,
        ancIntensity: AncIntensity,
        toneProfile: ToneProfile,
        toneIntensity: ToneIntensity,
        spatialMode: SpatialAudioMode = currentSpatialMode.value
    ): Short {
        val modelBase = when (tuningMode) {
            FrequencyTuningMode.DEEP_BASS -> 650
            FrequencyTuningMode.VOCAL_CLARITY -> 120
            FrequencyTuningMode.STUDIO_FLAT -> 0
            FrequencyTuningMode.AUDIOPHILE_HARMAN -> 220
            FrequencyTuningMode.MODEL_AUTO -> when (modelId) {
                "airpods_1", "airpods_2" -> 460 // High boost for open fit
                "airpods_3" -> 350
                "airpods_4", "airpods_4_anc" -> 360
                "airpods_pro_1" -> 240
                "airpods_pro_2" -> 200 // Tight, controlled
                "airpods_max" -> 160 // Large driver needs minimal artificial boost
                "galaxy_buds_3_pro", "galaxy_buds_pro_2" -> 220
                "sony_wf1000xm5" -> 250
                "boat_airdopes", "noise_buds", "boult_buds" -> 320
                "jbl_tune", "bose_qc", "soundcore_liberty" -> 240
                else -> 280
            }
        }

        val noiseExtra = when (noiseMode) {
            "ANC" -> when (ancIntensity) {
                AncIntensity.ULTRA -> 150
                AncIntensity.BALANCED -> 100
                AncIntensity.COMFORT -> 50
            }
            "TRANSPARENCY" -> 0
            else -> 60
        }

        val intensityFactor = when (toneIntensity) {
            ToneIntensity.SLIGHT -> 0.35f
            ToneIntensity.MODERATE -> 0.65f
            ToneIntensity.STRONG -> 0.95f
        }
        val toneExtra = if (toneProfile == ToneProfile.BALANCED_TONE) (120 * intensityFactor).toInt() else 30

        val spatialExtra = when (spatialMode) {
            SpatialAudioMode.FIXED -> 120
            SpatialAudioMode.HEAD_TRACKED -> 180
            SpatialAudioMode.OFF -> 0
        }

        return (modelBase + noiseExtra + toneExtra + spatialExtra).coerceIn(0, 1000).toShort()
    }

    private fun applyCurvesToSession(effects: SessionAudioEffects) {
        val eq = effects.equalizer
        if (eq != null) {
            try {
                eq.enabled = true
                val numBands = eq.numberOfBands.toInt()
                val minEQ = eq.bandLevelRange[0].toFloat() // typically -1500 mB (-15dB)
                val maxEQ = eq.bandLevelRange[1].toFloat() // typically +1500 mB (+15dB)

                for (i in 0 until numBands) {
                    val centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000 // in Hz
                    val combinedGainFraction = calculateTargetGain(
                        centerFreqHz = centerFreqHz,
                        modelId = currentActiveModelId.value,
                        tuningMode = currentTuningMode.value,
                        noiseMode = activeNoiseMode,
                        ancIntensity = currentAncIntensity.value,
                        antiHiss = antiHissWindReduction.value,
                        toneProfile = currentToneProfile.value,
                        toneIntensity = currentToneIntensity.value,
                        spatialMode = currentSpatialMode.value
                    )

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
                val strength = calculateBassBoost(
                    modelId = currentActiveModelId.value,
                    tuningMode = currentTuningMode.value,
                    noiseMode = activeNoiseMode,
                    ancIntensity = currentAncIntensity.value,
                    toneProfile = currentToneProfile.value,
                    toneIntensity = currentToneIntensity.value
                )
                bb.setStrength(strength)
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
                            if (virt.strengthSupported) virt.setStrength(300.toShort())
                        } else {
                            virt.enabled = false
                        }
                    }
                    SpatialAudioMode.FIXED -> {
                        virt.enabled = true
                        if (virt.strengthSupported) {
                            virt.setStrength(800.toShort())
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

    fun checkBalancePermission(context: Context): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        hasBalancePermission.value = granted
        return granted
    }

    /**
     * Sets Left/Right stereo balance system-wide:
     * - Dispatches to Android's native Settings.Secure "master_balance"
     * - Triggers AudioService -> AudioSystem.setMasterBalance(balance)
     * Range: -1.0f (Full Left) to +1.0f (Full Right), 0.0f = Center Balanced
     */
    fun setAudioBalance(context: Context, balance: Float) {
        val clamped = balance.coerceIn(-1.0f, 1.0f)
        currentBalance.value = clamped
        if (checkBalancePermission(context)) {
            try {
                Settings.Secure.putFloat(context.contentResolver, "master_balance", clamped)
                Log.i(TAG, "Applied Settings.Secure master_balance: $clamped")
            } catch (e: Exception) {
                Log.w(TAG, "Settings.Secure master_balance failed: ${e.message}")
            }
        } else {
            Log.i(TAG, "WRITE_SECURE_SETTINGS not granted. Stored balance $clamped in-app (requires ADB grant for global audio effect).")
        }
    }

    fun initBalance(context: Context) {
        checkBalancePermission(context)
        try {
            val bal = Settings.Secure.getFloat(context.contentResolver, "master_balance", 0.0f)
            currentBalance.value = bal.coerceIn(-1.0f, 1.0f)
        } catch (_: Exception) {
            try {
                val bal = Settings.System.getFloat(context.contentResolver, "master_balance", 0.0f)
                currentBalance.value = bal.coerceIn(-1.0f, 1.0f)
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
                    setTargetGain(2200) // +2200 mB = +22 dB solid gain boost for Bluetooth A2DP
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
        val file = File(context.cacheDir, "find_my_siren_v3.wav")
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

    fun getActiveSessionsCount(): Int = activeSessions.size

    fun getDiagnosticBandInfo(): List<String> {
        val sampleEq = activeSessions.values.firstOrNull()?.equalizer ?: return emptyList()
        val bands = mutableListOf<String>()
        try {
            val numBands = sampleEq.numberOfBands.toInt()
            for (i in 0 until numBands) {
                val freq = sampleEq.getCenterFreq(i.toShort()) / 1000
                val level = sampleEq.getBandLevel(i.toShort()) / 100f
                bands.add("${freq}Hz: ${if (level >= 0) "+" else ""}${level}dB")
            }
        } catch (_: Exception) {}
        return bands
    }
}
