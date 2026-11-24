package me.anypods.app.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.anypods.app.bluetooth.AirPodsL2capClient

enum class MicSelection(val byteVal: Byte, val label: String, val subtitle: String) {
    AUTO(0x00, "Auto-Switch", "Earbuds dynamically pick the active earbud with optimal acoustic pickup"),
    ALWAYS_LEFT(0x01, "Always Left Pod", "Locks audio capture to left pod (fixes dead/muffled right mic)"),
    ALWAYS_RIGHT(0x02, "Always Right Pod", "Locks audio capture to right pod (fixes dead/muffled left mic)")
}

/**
 * VoiceMicManager: High-definition voice capture & microphone clarity engine.
 * - Forces 16 kHz mSBC Wideband Speech (HD Voice) over Bluetooth SCO instead of legacy 8 kHz CVSD telephone audio.
 * - Manages hardware voice pre-processing DSP: Noise Suppressor, Echo Canceller, and Auto-Gain Control.
 * - Dispatches Left/Right/Auto microphone routing to AirPods/TWS hardware via L2CAP AACP.
 *
 * // ponytail: Native Android AudioEffect bindings & vendor bt_wbs parameters. Zero NDK/library bloat.
 */
object VoiceMicManager {
    private const val TAG = "AnyPods.VoiceMicManager"
    private const val PREFS_NAME = "anypods_prefs"
    private const val KEY_MIC_SELECTION = "voice_mic_selection"
    private const val KEY_HD_VOICE = "voice_hd_wideband"
    private const val KEY_VOICE_CLARITY_DSP = "voice_clarity_dsp"
    private const val KEY_MIC_BOOST_LEVEL = "voice_mic_boost_level"

    private var prefs: SharedPreferences? = null

    private val _micSelection = MutableStateFlow(MicSelection.AUTO)
    val micSelection = _micSelection.asStateFlow()

    private val _hdVoiceEnabled = MutableStateFlow(true)
    val hdVoiceEnabled = _hdVoiceEnabled.asStateFlow()

    private val _voiceClarityDspEnabled = MutableStateFlow(true)
    val voiceClarityDspEnabled = _voiceClarityDspEnabled.asStateFlow()

    private val _micBoostLevel = MutableStateFlow(1.5f) // 1.0f (Normal) to 2.5f (Max Boost)
    val micBoostLevel = _micBoostLevel.asStateFlow()

    private var activeNoiseSuppressor: NoiseSuppressor? = null
    private var activeEchoCanceler: AcousticEchoCanceler? = null
    private var activeGainControl: AutomaticGainControl? = null

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val savedMicOrdinal = sp.getInt(KEY_MIC_SELECTION, MicSelection.AUTO.ordinal)
        _micSelection.value = MicSelection.values().getOrElse(savedMicOrdinal) { MicSelection.AUTO }
        _hdVoiceEnabled.value = sp.getBoolean(KEY_HD_VOICE, true)
        _voiceClarityDspEnabled.value = sp.getBoolean(KEY_VOICE_CLARITY_DSP, true)
        _micBoostLevel.value = sp.getFloat(KEY_MIC_BOOST_LEVEL, 1.5f)

        if (_hdVoiceEnabled.value) {
            applyWidebandSpeech(context)
        }
    }

    fun setMicSelection(context: Context, selection: MicSelection) {
        _micSelection.value = selection
        prefs?.edit()?.putInt(KEY_MIC_SELECTION, selection.ordinal)?.apply()

        // Dispatch hardware opcode 0x11 over L2CAP channel
        AirPodsL2capClient.sendMicSelection(selection)
        Log.i(TAG, "Microphone selection updated: ${selection.label}")
    }

    fun setHdVoiceEnabled(context: Context, enabled: Boolean) {
        _hdVoiceEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_HD_VOICE, enabled)?.apply()

        if (enabled) {
            applyWidebandSpeech(context)
        } else {
            disableWidebandSpeech(context)
        }
        Log.i(TAG, "HD Voice Wideband (mSBC 16kHz) toggled: $enabled")
    }

    fun setVoiceClarityDspEnabled(enabled: Boolean) {
        _voiceClarityDspEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_VOICE_CLARITY_DSP, enabled)?.apply()
        Log.i(TAG, "Voice clarity DSP toggled: $enabled")
    }

    fun setMicBoostLevel(level: Float) {
        _micBoostLevel.value = level
        prefs?.edit()?.putFloat(KEY_MIC_BOOST_LEVEL, level)?.apply()
        Log.i(TAG, "Mic boost level set to: ${level}x")
    }

    /**
     * Instructs Android AudioFlinger and Bluetooth HAL to negotiate 16 kHz mSBC Wideband Speech
     * for Bluetooth SCO headsets rather than downgrading to 8 kHz CVSD narrow-band voice.
     */
    fun applyWidebandSpeech(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.setParameters("bt_wbs=on")
            am.setParameters("bt_headset_wbs=on")
            am.setParameters("bt_sco_samplerate=16000")
            Log.d(TAG, "Wideband speech parameters enforced: bt_wbs=on (16kHz)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting wideband voice parameters: ${e.message}")
        }
    }

    private fun disableWidebandSpeech(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.setParameters("bt_wbs=off")
        } catch (_: Exception) {}
    }

    /**
     * Attaches hardware audio processing effects (Noise Suppression, Acoustic Echo Cancellation,
     * and Automatic Gain Control) to an active microphone audio session.
     */
    fun attachVoiceDspEffects(audioSessionId: Int) {
        if (!_voiceClarityDspEnabled.value || audioSessionId <= 0) return
        try {
            releaseVoiceDspEffects()

            if (NoiseSuppressor.isAvailable()) {
                activeNoiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
                Log.d(TAG, "Hardware NoiseSuppressor active on session $audioSessionId")
            }

            if (AcousticEchoCanceler.isAvailable()) {
                activeEchoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
                Log.d(TAG, "Hardware AcousticEchoCanceler active on session $audioSessionId")
            }

            if (AutomaticGainControl.isAvailable()) {
                activeGainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                }
                Log.d(TAG, "Hardware AutomaticGainControl active on session $audioSessionId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error attaching voice DSP effects: ${e.message}")
        }
    }

    fun releaseVoiceDspEffects() {
        try {
            activeNoiseSuppressor?.release()
            activeEchoCanceler?.release()
            activeGainControl?.release()
        } catch (_: Exception) {}
        activeNoiseSuppressor = null
        activeEchoCanceler = null
        activeGainControl = null
    }
}
