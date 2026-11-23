package me.anypods.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Precision Proximity & Distance Tracker for wireless earbuds.
 * Measures live Bluetooth RSSI (Signal Strength) and calculates real-time distance
 * with haptic radar feedback.
 */
object ProximityTracker {
    private const val TAG = "ProximityTracker"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var trackingJob: Job? = null
    private var activeGatt: BluetoothGatt? = null

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _rssiDbm = MutableStateFlow(-100)
    val rssiDbm: StateFlow<Int> = _rssiDbm.asStateFlow()

    private val _distanceMeters = MutableStateFlow(0f)
    val distanceMeters: StateFlow<Float> = _distanceMeters.asStateFlow()

    private val _signalPercent = MutableStateFlow(0)
    val signalPercent: StateFlow<Int> = _signalPercent.asStateFlow()

    private val _proximityStatus = MutableStateFlow("Searching...")
    val proximityStatus: StateFlow<String> = _proximityStatus.asStateFlow()

    private var smoothedRssi = -75.0
    private var lastHapticTime = 0L

    @SuppressLint("MissingPermission")
    fun startTracking(context: Context, targetAddress: String) {
        if (_isTracking.value) stopTracking()
        _isTracking.value = true
        _proximityStatus.value = "Calibrating distance..."

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter ?: return

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        // Try GATT RSSI polling if device is bonded/connected
        val device = try {
            adapter.getRemoteDevice(targetAddress)
        } catch (e: Exception) {
            null
        }

        if (device != null) {
            try {
                activeGatt = device.connectGatt(context.applicationContext, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            gatt?.readRemoteRssi()
                        }
                    }

                    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            processNewRssi(rssi, vibrator)
                        }
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "GATT connection for RSSI failed: ${e.message}")
            }
        }

        // Also start low-latency BLE scan for constant real-time beacon updates
        val scanner = adapter.bluetoothLeScanner
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let {
                    if (it.device.address.equals(targetAddress, ignoreCase = true)) {
                        processNewRssi(it.rssi, vibrator)
                    }
                }
            }
        }

        val scanFilter = try {
            listOf(ScanFilter.Builder().setDeviceAddress(targetAddress).build())
        } catch (e: Exception) {
            emptyList()
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            if (scanFilter.isNotEmpty()) {
                scanner?.startScan(scanFilter, scanSettings, scanCallback)
            } else {
                scanner?.startScan(scanCallback)
            }
        } catch (e: Exception) {
            Log.w(TAG, "BLE scan start error: ${e.message}")
        }

        trackingJob = scope.launch {
            while (isActive && _isTracking.value) {
                // Poll connected GATT RSSI every 700ms
                try {
                    activeGatt?.readRemoteRssi()
                } catch (e: Exception) {
                    // Ignore
                }
                delay(700)
            }
            try {
                scanner?.stopScan(scanCallback)
                activeGatt?.close()
                activeGatt = null
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun processNewRssi(rawRssi: Int, vibrator: Vibrator?) {
        if (rawRssi < -110 || rawRssi > -20) return
        smoothedRssi = smoothedRssi * 0.65 + rawRssi * 0.35

        val rssiInt = smoothedRssi.toInt()
        _rssiDbm.value = rssiInt

        // Standard logarithmic path loss equation: Distance = 10 ^ ((TxPower - RSSI) / (20 * N))
        // Standard TxPower calibration at 1m for TWS earbuds is -59 dBm, indoor path loss exponent N = 2.2
        val dist = 10.0.pow((-59.0 - smoothedRssi) / 44.0).toFloat().coerceIn(0.2f, 25.0f)
        _distanceMeters.value = String.format("%.1f", dist).toFloat()

        val pct = (((smoothedRssi + 95.0) / 55.0) * 100.0).toInt().coerceIn(2, 99)
        _signalPercent.value = pct

        val status = when {
            dist <= 0.8f -> "Within 1 Meter • Very Close 🔥"
            dist <= 2.2f -> "Getting Closer! Nearby 🟡"
            dist <= 5.0f -> "Searching Area • In Room 🟢"
            else -> "Signal Weak • Move Around ❄️"
        }
        _proximityStatus.value = status

        // Haptic feedback proportional to proximity
        val now = System.currentTimeMillis()
        val hapticInterval = when {
            dist <= 0.8f -> 220L
            dist <= 2.0f -> 450L
            dist <= 4.0f -> 850L
            else -> 1800L
        }

        if (now - lastHapticTime >= hapticInterval && vibrator != null && vibrator.hasVibrator()) {
            lastHapticTime = now
            val duration = if (dist <= 1.0f) 50L else 30L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }
    }

    fun stopTracking() {
        _isTracking.value = false
        trackingJob?.cancel()
        trackingJob = null
        try {
            activeGatt?.close()
            activeGatt = null
        } catch (e: Exception) {
            // Ignore
        }
    }
}
