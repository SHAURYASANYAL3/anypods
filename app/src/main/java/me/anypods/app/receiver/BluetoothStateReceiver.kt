package me.anypods.app.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import me.anypods.app.bluetooth.BluetoothTracker
import me.anypods.app.service.EarbudService
import me.anypods.app.ui.OverlayPopup

class BluetoothStateReceiver : BroadcastReceiver() {
    private val TAG = "AnyPods.BTReceiver"

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received broadcast action: $action")

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                if (device != null) {
                    BluetoothTracker.onAclConnected(device)
                    // Trigger AirPods style popup!
                    OverlayPopup.show(context, BluetoothTracker.earbudState.value)
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                if (device != null) {
                    BluetoothTracker.onAclDisconnected(device)
                    OverlayPopup.dismiss()
                }
            }
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                if (device != null && level >= 0) {
                    BluetoothTracker.onBatteryChanged(device, level)
                }
            }
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                BluetoothTracker.refreshConnectedDevice()
            }
        }

        // Ensure service is running
        val serviceIntent = Intent(context, EarbudService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start EarbudService from receiver: ${e.message}")
        }
    }
}
