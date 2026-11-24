package me.anypods.app.diagnostics

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.anypods.app.anc.NoiseControlManager
import me.anypods.app.audio.AudioEnhancer
import me.anypods.app.model.EarbudState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

object DiagnosticManager {
    private const val TAG = "AnyPods.DiagnosticManager"
    private const val PREFS_NAME = "anypods_diagnostic_prefs"
    private const val KEY_CONSENT_GRANTED = "consent_share_diagnostics"
    private const val KEY_PROMPT_SHOWN = "consent_prompt_shown"

    private const val MAX_LOG_EVENTS = 120
    private val logEvents = ConcurrentLinkedDeque<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val _consentGranted = MutableStateFlow(false)
    val consentGranted: StateFlow<Boolean> = _consentGranted.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        _consentGranted.value = sp.getBoolean(KEY_CONSENT_GRANTED, false)
        logEvent("System", "DiagnosticManager initialized. Sharing consent: ${_consentGranted.value}")
    }

    fun isConsentGranted(context: Context): Boolean {
        val sp = prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_CONSENT_GRANTED, false)
    }

    fun setConsentGranted(context: Context, granted: Boolean) {
        val sp = prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean(KEY_CONSENT_GRANTED, granted)
            .putBoolean(KEY_PROMPT_SHOWN, true)
            .apply()
        _consentGranted.value = granted
        logEvent("Consent", "User updated diagnostic sharing preference: granted=$granted")
    }

    fun shouldShowConsentPrompt(context: Context): Boolean {
        val sp = prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !sp.getBoolean(KEY_PROMPT_SHOWN, false)
    }

    fun markConsentPromptShown(context: Context) {
        val sp = prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_PROMPT_SHOWN, true).apply()
    }

    fun logEvent(source: String, message: String) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val entry = "[$timestamp] [$source] $message"
        logEvents.addLast(entry)
        while (logEvents.size > MAX_LOG_EVENTS) {
            logEvents.pollFirst()
        }
        Log.d(TAG, entry)
    }

    fun maskAddress(mac: String): String {
        if (mac.isBlank()) return "None"
        val parts = mac.split(":")
        return if (parts.size == 6) {
            "${parts[0]}:${parts[1]}:${parts[2]}:**:**:**"
        } else {
            mac.take(4) + "****"
        }
    }

    fun buildDiagnosticReport(context: Context, state: EarbudState?): String {
        val sb = StringBuilder()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())

        val appVersionName = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pInfo.versionName} (${pInfo.versionCode})"
        } catch (_: Exception) {
            "Unknown"
        }

        sb.appendLine("====================================================")
        sb.appendLine("        ANYPODS PRO - SYSTEM & AUDIO DIAGNOSTIC REPORT")
        sb.appendLine("====================================================")
        sb.appendLine("Generated At       : $timestamp")
        sb.appendLine("App Version        : $appVersionName")
        sb.appendLine("Sharing Consent    : ${if (isConsentGranted(context)) "GRANTED (Opt-in)" else "NOT GRANTED"}")
        sb.appendLine("Privacy Protection : All MAC addresses masked; no personal")
        sb.appendLine("                     identifiers or audio recordings collected.")
        sb.appendLine("====================================================")
        sb.appendLine()

        // Device & Android OS
        sb.appendLine("--- [DEVICE & PLATFORM] ---")
        sb.appendLine("Manufacturer       : ${Build.MANUFACTURER}")
        sb.appendLine("Brand / Model      : ${Build.BRAND} ${Build.MODEL} (${Build.DEVICE})")
        sb.appendLine("Board / Hardware   : ${Build.BOARD} / ${Build.HARDWARE}")
        sb.appendLine("Android OS Version : Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Supported ABIs     : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        sb.appendLine()

        // Earbuds Status
        val s = state ?: EarbudState()
        sb.appendLine("--- [CONNECTED EARBUDS HARDWARE] ---")
        sb.appendLine("Device Name        : ${s.name}")
        sb.appendLine("Hardware Address   : ${maskAddress(s.address)}")
        sb.appendLine("Connection Status  : ${if (s.isConnected) "Connected" else "Disconnected"}")
        sb.appendLine("Detected Model ID  : ${s.modelId}")
        sb.appendLine("Battery Left       : ${if (s.leftBattery >= 0) "${s.leftBattery}%" else "N/A"}${if (s.isLeftCharging) " [⚡Charging]" else ""}${if (s.isLeftInEar) " [In-Ear]" else " [Out-of-Ear]"}")
        sb.appendLine("Battery Right      : ${if (s.rightBattery >= 0) "${s.rightBattery}%" else "N/A"}${if (s.isRightCharging) " [⚡Charging]" else ""}${if (s.isRightInEar) " [In-Ear]" else " [Out-of-Ear]"}")
        sb.appendLine("Battery Case       : ${if (s.caseBattery >= 0) "${s.caseBattery}%" else "N/A"}${if (s.isCaseCharging) " [⚡Charging]" else ""}")
        sb.appendLine("Audio Codec        : ${s.codec}")
        sb.appendLine()

        // Audio Subsystem & Frequency Tuning
        sb.appendLine("--- [DSP & FREQUENCY TUNING ENGINE] ---")
        sb.appendLine("Tuning Mode        : ${AudioEnhancer.currentTuningMode.value.title}")
        sb.appendLine("Target Model Spec  : ${AudioEnhancer.currentActiveModelId.value}")
        sb.appendLine("Spatial Audio      : ${AudioEnhancer.currentSpatialMode.value.name}")
        sb.appendLine("Tone Accommodations: ${AudioEnhancer.currentToneProfile.value.name} (${AudioEnhancer.currentToneIntensity.value.name})")
        sb.appendLine("Anti-Hiss/Wind Red : ${if (AudioEnhancer.antiHissWindReduction.value) "Enabled" else "Disabled"}")
        sb.appendLine("Stereo Balance     : ${AudioEnhancer.currentBalance.value} (Secure Settings: ${AudioEnhancer.hasBalancePermission.value})")
        sb.appendLine("Active FX Sessions : ${AudioEnhancer.getActiveSessionsCount()}")
        val bands = AudioEnhancer.getDiagnosticBandInfo()
        if (bands.isNotEmpty()) {
            sb.appendLine("Equalizer Bands    :")
            bands.forEach { band ->
                sb.appendLine("  • $band")
            }
        } else {
            sb.appendLine("Equalizer Bands    : Default / Inactive session")
        }
        sb.appendLine()

        // Noise Control
        sb.appendLine("--- [NOISE CONTROL & SENSING] ---")
        sb.appendLine("Current Mode       : ${NoiseControlManager.currentMode.value}")
        sb.appendLine("ANC Intensity      : ${AudioEnhancer.currentAncIntensity.value}")
        sb.appendLine("Ambient Decibels   : ${NoiseControlManager.ambientDecibels.value} dB")
        sb.appendLine("Conv. Awareness    : ${NoiseControlManager.conversationAwarenessEnabled.value}")
        sb.appendLine("Personalized Vol   : ${NoiseControlManager.personalizedVolumeEnabled.value}")
        sb.appendLine("Loud Sound Reduct. : ${NoiseControlManager.loudSoundReductionEnabled.value}")
        sb.appendLine()

        // Recent System Events
        sb.appendLine("--- [RECENT DIAGNOSTIC EVENTS (Last ${logEvents.size})] ---")
        if (logEvents.isEmpty()) {
            sb.appendLine("(No logged events recorded yet)")
        } else {
            logEvents.forEach { event ->
                sb.appendLine(event)
            }
        }
        sb.appendLine("====================================================")
        sb.appendLine("               END OF DIAGNOSTIC REPORT             ")
        sb.appendLine("====================================================")

        return sb.toString()
    }

    fun shareReport(context: Context, state: EarbudState?) {
        try {
            val report = buildDiagnosticReport(context, state)
            val file = File(context.cacheDir, "anypods_diagnostic_report.txt")
            file.writeText(report)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "[AnyPods Diagnostic Report] ${Build.MANUFACTURER} ${Build.MODEL} - ${state?.name ?: "Earbuds"}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Here is my AnyPods diagnostic log report to help improve compatibility with my AirPods / wireless earbuds.\n\n" +
                    "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n" +
                    "Earbuds: ${state?.name ?: "Unknown"} (Model: ${state?.modelId ?: "universal"})\n\n" +
                    "Full diagnostic report is attached."
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share AnyPods Diagnostics via...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            logEvent("Share", "Dispatched diagnostic report share sheet")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share diagnostic report: ${e.message}", e)
            Toast.makeText(context, "Failed to export diagnostic report: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
