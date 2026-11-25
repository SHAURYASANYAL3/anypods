package me.anypods.app.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.anypods.app.MainActivity
import me.anypods.app.R
import me.anypods.app.diagnostics.DiagnosticManager
import me.anypods.app.model.EarbudState

/**
 * In-Ear Detection & Smart Media Automation.
 * Inspired by CAPod and LibrePods: Auto-pauses media when an earbud is removed,
 * resumes playback upon re-insertion, and alerts when battery drops below 15%.
 */
object InEarDetectionManager {
    private const val TAG = "AnyPods.InEar"
    private const val PREFS_NAME = "anypods_in_ear_prefs"
    private const val KEY_AUTO_PAUSE = "auto_pause_enabled"
    private const val KEY_AUTO_RESUME = "auto_resume_enabled"
    private const val KEY_LOW_BATTERY_ALERT = "low_battery_alert_enabled"
    private const val KEY_EAR_CHIME = "ear_chime_enabled"
    private const val NOTIF_CHANNEL_ID = "anypods_battery_alerts"

    private val _autoPauseEnabled = MutableStateFlow(true)
    val autoPauseEnabled: StateFlow<Boolean> = _autoPauseEnabled.asStateFlow()

    private val _autoResumeEnabled = MutableStateFlow(true)
    val autoResumeEnabled: StateFlow<Boolean> = _autoResumeEnabled.asStateFlow()

    private val _lowBatteryAlertEnabled = MutableStateFlow(true)
    val lowBatteryAlertEnabled: StateFlow<Boolean> = _lowBatteryAlertEnabled.asStateFlow()

    private val _earChimeEnabled = MutableStateFlow(true)
    val earChimeEnabled: StateFlow<Boolean> = _earChimeEnabled.asStateFlow()

    private var wasInEar: Boolean? = null
    private var wasPausedByUs = false
    private var lastLeftAlerted = 100
    private var lastRightAlerted = 100
    private var lastCaseAlerted = 100
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _autoPauseEnabled.value = prefs.getBoolean(KEY_AUTO_PAUSE, true)
        _autoResumeEnabled.value = prefs.getBoolean(KEY_AUTO_RESUME, true)
        _lowBatteryAlertEnabled.value = prefs.getBoolean(KEY_LOW_BATTERY_ALERT, true)
        _earChimeEnabled.value = prefs.getBoolean(KEY_EAR_CHIME, true)
        createAlertNotificationChannel(context)
        isInitialized = true
    }

    fun setAutoPauseEnabled(context: Context, enabled: Boolean) {
        _autoPauseEnabled.value = enabled
        savePref(context, KEY_AUTO_PAUSE, enabled)
    }

    fun setAutoResumeEnabled(context: Context, enabled: Boolean) {
        _autoResumeEnabled.value = enabled
        savePref(context, KEY_AUTO_RESUME, enabled)
    }

    fun setLowBatteryAlertEnabled(context: Context, enabled: Boolean) {
        _lowBatteryAlertEnabled.value = enabled
        savePref(context, KEY_LOW_BATTERY_ALERT, enabled)
    }

    fun setEarChimeEnabled(context: Context, enabled: Boolean) {
        _earChimeEnabled.value = enabled
        savePref(context, KEY_EAR_CHIME, enabled)
    }

    private fun savePref(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    fun onEarbudStateChanged(context: Context, state: EarbudState) {
        init(context)

        if (!state.isConnected) {
            wasInEar = null
            wasPausedByUs = false
            return
        }

        val leftActive = state.leftBattery >= 0 && !state.isLeftCharging
        val rightActive = state.rightBattery >= 0 && !state.isRightCharging

        if (!leftActive && !rightActive) {
            wasInEar = null
            return
        }

        // Determine currently worn in-ear state
        val isInEar = when {
            leftActive && rightActive -> state.isLeftInEar && state.isRightInEar
            leftActive -> state.isLeftInEar
            else -> state.isRightInEar
        }

        val prevInEar = wasInEar
        wasInEar = isInEar

        if (prevInEar != null && prevInEar != isInEar) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (isInEar) {
                // Earbud inserted into ear
                if (wasPausedByUs && _autoResumeEnabled.value) {
                    resumeMedia(context)
                    wasPausedByUs = false
                    DiagnosticManager.logEvent("InEar", "Pod returned to ear -> Resumed media")
                }
                if (_earChimeEnabled.value) {
                    AppleSoundChimes.playInEarChime(context)
                }
            } else {
                // Earbud removed from ear
                if (_autoPauseEnabled.value) {
                    val isPlaying = am?.isMusicActive == true
                    if (isPlaying) {
                        wasPausedByUs = true
                        pauseMedia(context)
                        DiagnosticManager.logEvent("InEar", "Pod removed from ear -> Paused media")
                    }
                }
            }
        }

        // Check for low battery warnings (< 15%)
        if (_lowBatteryAlertEnabled.value) {
            checkLowBattery(context, "Left Pod", state.leftBattery, state.isLeftCharging, lastLeftAlerted) { lastLeftAlerted = it }
            checkLowBattery(context, "Right Pod", state.rightBattery, state.isRightCharging, lastRightAlerted) { lastRightAlerted = it }
            checkLowBattery(context, "Case", state.caseBattery, state.isCaseCharging, lastCaseAlerted) { lastCaseAlerted = it }
        }
    }

    private fun checkLowBattery(
        context: Context,
        label: String,
        battery: Int,
        isCharging: Boolean,
        lastAlerted: Int,
        updateLastAlerted: (Int) -> Unit
    ) {
        if (battery in 1..15 && !isCharging && lastAlerted > 15) {
            updateLastAlerted(battery)
            postLowBatteryNotification(context, label, battery)
            AppleSoundChimes.playLowBatteryChime(context)
            DiagnosticManager.logEvent("BatteryAlert", "$label battery critical: $battery%")
        } else if (isCharging || battery > 20) {
            updateLastAlerted(100)
        }
    }

    private fun pauseMedia(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
            Log.d(TAG, "Sent KEYCODE_MEDIA_PAUSE")
        } catch (e: Exception) {
            Log.w(TAG, "pauseMedia error: ${e.message}")
        }
    }

    private fun resumeMedia(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            Log.d(TAG, "Sent KEYCODE_MEDIA_PLAY")
        } catch (e: Exception) {
            Log.w(TAG, "resumeMedia error: ${e.message}")
        }
    }

    private fun createAlertNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Battery Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical low battery warnings for earbuds and case"
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun postLowBatteryNotification(context: Context, item: String, battery: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.airpods)
                .setContentTitle("⚠️ $item Battery Low ($battery%)")
                .setContentText("Place $item in the charging case to avoid interruption.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()

            nm.notify(2001 + item.hashCode(), notif)
        } catch (e: Exception) {
            Log.w(TAG, "postLowBatteryNotification failed: ${e.message}")
        }
    }
}
