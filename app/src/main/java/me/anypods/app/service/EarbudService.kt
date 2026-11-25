package me.anypods.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.anypods.app.MainActivity
import me.anypods.app.R
import me.anypods.app.audio.AudioEnhancer
import me.anypods.app.bluetooth.BluetoothTracker
import me.anypods.app.model.EarbudState
import me.anypods.app.model.PodModelManager
import me.anypods.app.receiver.AudioEffectReceiver

class EarbudService : Service() {
    private val CHANNEL_ID = "anypods_status_channel"
    private val NOTIF_ID = 1001
    private val scope = CoroutineScope(Dispatchers.Main)
    private var stateJob: Job? = null
    private val audioEffectReceiver = AudioEffectReceiver()
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundWithNotification(BluetoothTracker.earbudState.value)
        AudioEnhancer.initAudioEffects(this)
        me.anypods.app.audio.VoiceMicManager.init(this)
        me.anypods.app.audio.InEarDetectionManager.init(this)

        // Register dynamic audio effect session broadcast receiver for Spotify / media players
        try {
            val filter = IntentFilter().apply {
                addAction(AudioEffectReceiver.ACTION_OPEN_CONTROL)
                addAction(AudioEffectReceiver.ACTION_CLOSE_CONTROL)
                addAction(AudioEffectReceiver.ACTION_OPEN_SESSION)
                addAction(AudioEffectReceiver.ACTION_CLOSE_SESSION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(audioEffectReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(audioEffectReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Live Audio Playback Tracking for YouTube, browsers, streaming apps & games
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (am != null) {
                    val cb = object : AudioManager.AudioPlaybackCallback() {
                        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                            AudioEnhancer.scanActiveSessions(this@EarbudService)
                            for (cfg in configs) {
                                val sId = AudioEnhancer.extractSessionId(cfg)
                                if (sId != null && sId > 0) {
                                    AudioEnhancer.registerSession(sId)
                                }
                            }
                        }
                    }
                    am.registerAudioPlaybackCallback(cb, Handler(Looper.getMainLooper()))
                    playbackCallback = cb
                }
            } catch (e: Exception) {
                Log.w("AnyPods.EarbudService", "Failed to register audio playback callback: ${e.message}")
            }
        }

        stateJob = scope.launch {
            BluetoothTracker.earbudState.collectLatest { state ->
                updateNotification(state)
                me.anypods.app.audio.InEarDetectionManager.onEarbudStateChanged(this@EarbudService, state)
            }
        }

        // Periodic background update check
        me.anypods.app.updater.UpdateManager.init(this)
        me.anypods.app.updater.UpdateManager.checkForUpdates(this, isManual = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BluetoothTracker.refreshConnectedDevice()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateJob?.cancel()
        try {
            unregisterReceiver(audioEffectReceiver)
        } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playbackCallback?.let {
                try {
                    val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    am?.unregisterAudioPlaybackCallback(it)
                } catch (_: Exception) {}
            }
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Earbuds Status"
            val descriptionText = "Displays real-time battery status of connected earbuds"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: EarbudState): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val modelInfo = PodModelManager.getModelById(state.modelId)
        val title = if (state.isConnected) "${state.name} (${modelInfo.name})" else "AnyPods - Ready"
        val leftStr = if (state.leftBattery >= 0) "${state.leftBattery}%" + (if (state.isLeftCharging) " ⚡" else "") else "--%"
        val rightStr = if (state.rightBattery >= 0) "${state.rightBattery}%" + (if (state.isRightCharging) " ⚡" else "") else "--%"
        val caseStr = if (state.caseBattery >= 0) "${state.caseBattery}%" + (if (state.isCaseCharging) " ⚡" else "") else "--%"

        val content = if (state.isConnected) {
            "L: $leftStr  |  Case: $caseStr  |  R: $rightStr"
        } else {
            "Connect your wireless earbuds to view battery & sound controls"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.airpods)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startForegroundWithNotification(state: EarbudState) {
        val notification = buildNotification(state)
        try {
            val hasBtConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val hasRecordAudio = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            var fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasRecordAudio) {
                fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (hasBtConnect) {
                    startForeground(NOTIF_ID, notification, fgsType)
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIF_ID, notification)
                } else {
                    Log.w("AnyPods.EarbudService", "BLUETOOTH_CONNECT permission missing on Android 14+; deferring foreground service")
                    stopSelf()
                }
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.w("AnyPods.EarbudService", "startForeground error: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIF_ID, notification)
                } else {
                    stopSelf()
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateNotification(state: EarbudState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification(state))
    }

    companion object {
        fun startServiceSafely(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    Log.d("AnyPods.EarbudService", "BLUETOOTH_CONNECT not granted yet; waiting for user permission")
                    return
                }
            }
            val intent = Intent(context, EarbudService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("AnyPods.EarbudService", "Could not start EarbudService: ${e.message}")
            }
        }
    }
}
