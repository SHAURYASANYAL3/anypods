package me.anypods.app.audio

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object HearingHealthManager {
    private val _isLimiterEnabled = MutableStateFlow(true)
    val isLimiterEnabled = _isLimiterEnabled.asStateFlow()

    private val _estimatedDecibels = MutableStateFlow(72)
    val estimatedDecibels = _estimatedDecibels.asStateFlow()

    fun toggleLimiter(context: Context, enabled: Boolean) {
        _isLimiterEnabled.value = enabled
        if (enabled) {
            enforceSafeVolume(context)
        }
    }

    fun updateEstimatedDb(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        val pct = current.toFloat() / max
        // Estimate SPL dB: 0% = ~45dB ambient, 100% = ~102dB peak
        val db = (45 + pct * 57).toInt()
        _estimatedDecibels.value = db

        if (_isLimiterEnabled.value && db > 85) {
            enforceSafeVolume(context)
        }
    }

    private fun enforceSafeVolume(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        // 85dB corresponds to ~70% max volume
        val safeVolume = (max * 0.70f).toInt()
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > safeVolume) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, safeVolume, 0)
            _estimatedDecibels.value = 85
        }
    }
}
