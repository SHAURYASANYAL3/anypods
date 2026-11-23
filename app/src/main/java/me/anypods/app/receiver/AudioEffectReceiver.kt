package me.anypods.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log
import me.anypods.app.audio.AudioEnhancer

class AudioEffectReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AnyPods.AudioReceiver"
        const val ACTION_OPEN_CONTROL = "android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION"
        const val ACTION_CLOSE_CONTROL = "android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION"
        const val ACTION_OPEN_SESSION = "android.media.action.OPEN_AUDIO_EFFECT_SESSION"
        const val ACTION_CLOSE_SESSION = "android.media.action.CLOSE_AUDIO_EFFECT_SESSION"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown"

        Log.d(TAG, "Audio effect broadcast received: action=$action, session=$sessionId, pkg=$pkg")

        // ponytail: drop malformed/out-of-range sessions to protect native audioserver from DoS
        if (sessionId == AudioEffect.ERROR_BAD_VALUE || sessionId <= 0 || sessionId > 500000) return

        when (action) {
            ACTION_OPEN_CONTROL, ACTION_OPEN_SESSION -> {
                Log.i(TAG, "Attaching AnyPods Master DSP to player session $sessionId ($pkg)")
                AudioEnhancer.registerSession(sessionId)
            }
            ACTION_CLOSE_CONTROL, ACTION_CLOSE_SESSION -> {
                Log.i(TAG, "Releasing AnyPods Master DSP from player session $sessionId ($pkg)")
                AudioEnhancer.unregisterSession(sessionId)
            }
        }
    }
}
