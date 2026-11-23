package me.anypods.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.anypods.app.MainActivity
import me.anypods.app.R
import me.anypods.app.bluetooth.BluetoothTracker
import me.anypods.app.model.EarbudState

class EarbudService : Service() {
    private val CHANNEL_ID = "anypods_status_channel"
    private val NOTIF_ID = 1001
    private val scope = CoroutineScope(Dispatchers.Main)
    private var stateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundWithNotification(BluetoothTracker.earbudState.value)

        stateJob = scope.launch {
            BluetoothTracker.earbudState.collectLatest { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BluetoothTracker.refreshConnectedDevice()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateJob?.cancel()
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (state.isConnected) state.name else "AnyPods - Ready"
        val leftStr = if (state.leftBattery >= 0) "${state.leftBattery}%" else "--%"
        val rightStr = if (state.rightBattery >= 0) "${state.rightBattery}%" else "--%"
        val caseStr = if (state.caseBattery >= 0) "${state.caseBattery}%" else "--%"

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            try {
                startForeground(NOTIF_ID, notification)
            } catch (e2: Exception) {
                // Ignore if started in background without permission
            }
        }
    }

    private fun updateNotification(state: EarbudState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification(state))
    }
}
