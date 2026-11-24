package me.anypods.app.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
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
                if (device != null && BluetoothTracker.isAudioDevice(device)) {
                    BluetoothTracker.onAclConnected(device)
                    OverlayPopup.show(context, BluetoothTracker.earbudState.value)
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                if (device != null) {
                    BluetoothTracker.onAclDisconnected(device)
                    OverlayPopup.dismiss()
                }
            }
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    if (device != null && BluetoothTracker.isAudioDevice(device)) {
                        BluetoothTracker.onAclConnected(device)
                        OverlayPopup.show(context, BluetoothTracker.earbudState.value)
                    } else {
                        BluetoothTracker.refreshConnectedDevice()
                    }
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    BluetoothTracker.refreshConnectedDevice()
                }
            }
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                if (device != null && level >= 0) {
                    BluetoothTracker.onBatteryChanged(device, level)
                }
            }
            BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT -> {
                handleVendorSpecificEvent(device, intent)
            }
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                BluetoothTracker.refreshConnectedDevice()
            }
        }

        // Ensure service is running safely
        EarbudService.startServiceSafely(context)
    }

    private fun handleVendorSpecificEvent(device: BluetoothDevice?, intent: Intent) {
        if (device == null) return
        val cmd = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD) ?: return
        val args = intent.getSerializableExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS) as? Array<*> ?: return

        if (cmd == "+IPHONEACCEV" && args.isNotEmpty()) {
            parseIphoneAccev(device, args)
        }
    }

    private fun parseIphoneAccev(device: BluetoothDevice, args: Array<*>) {
        try {
            // +IPHONEACCEV format: [key_count, key1, val1, key2, val2, ...]
            if (args.isEmpty()) return
            val count = (args[0] as? Number)?.toInt() ?: ((args[0] as? String)?.toIntOrNull() ?: return)
            var index = 1
            var leftBat = -1
            var rightBat = -1
            var singleBat = -1

            for (i in 0 until count) {
                if (index + 1 >= args.size) break
                val key = (args[index] as? Number)?.toInt() ?: (args[index] as? String)?.toIntOrNull()
                val value = (args[index + 1] as? Number)?.toInt() ?: (args[index + 1] as? String)?.toIntOrNull()
                index += 2

                if (key != null && value != null) {
                    when (key) {
                        1 -> {
                            // Standard Battery level: 0..9 representing 10% to 100%
                            val pct = ((value + 1) * 10).coerceIn(10, 100)
                            singleBat = pct
                        }
                        2 -> {
                            // Left earbud or docked
                            val pct = ((value + 1) * 10).coerceIn(10, 100)
                            leftBat = pct
                        }
                        3 -> {
                            // Right earbud
                            val pct = ((value + 1) * 10).coerceIn(10, 100)
                            rightBat = pct
                        }
                    }
                }
            }

            if (leftBat in 0..100 && rightBat in 0..100) {
                BluetoothTracker.onDualBatteryChanged(device, leftBat, rightBat)
            } else if (singleBat in 0..100) {
                BluetoothTracker.onBatteryChanged(device, singleBat)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing +IPHONEACCEV: ${e.message}")
        }
    }
}
