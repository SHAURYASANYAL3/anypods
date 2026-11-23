package me.anypods.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.anypods.app.model.EarbudState
import java.util.UUID

@SuppressLint("MissingPermission")
object BluetoothTracker {
    private const val TAG = "AnyPods.BluetoothTracker"
    private const val PREFS_NAME = "anypods_prefs"
    private const val KEY_CASE_BATTERY = "case_battery"
    private const val KEY_CASE_CHARGING = "case_charging"
    private const val KEY_LEFT_CHARGING = "left_charging"
    private const val KEY_RIGHT_CHARGING = "right_charging"

    private val scope = CoroutineScope(Dispatchers.Main)

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val _earbudState = MutableStateFlow(EarbudState())
    val earbudState: StateFlow<EarbudState> = _earbudState.asStateFlow()

    // Standard Battery Service UUID: 0000180F-0000-1000-8000-00805F9B34FB
    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bm?.adapter

        val savedCase = prefs?.getInt(KEY_CASE_BATTERY, 50) ?: 50
        val savedCaseCharging = prefs?.getBoolean(KEY_CASE_CHARGING, false) ?: false
        _earbudState.value = _earbudState.value.copy(
            caseBattery = savedCase,
            isCaseCharging = savedCaseCharging
        )

        refreshConnectedDevice()
    }

    fun setCaseBattery(level: Int, isCharging: Boolean = false) {
        val clamped = level.coerceIn(0, 100)
        prefs?.edit()?.putInt(KEY_CASE_BATTERY, clamped)?.putBoolean(KEY_CASE_CHARGING, isCharging)?.apply()
        _earbudState.value = _earbudState.value.copy(
            caseBattery = clamped,
            isCaseCharging = isCharging
        )
    }

    fun setBudCharging(left: Boolean, charging: Boolean) {
        val current = _earbudState.value
        val updated = if (left) {
            prefs?.edit()?.putBoolean(KEY_LEFT_CHARGING, charging)?.apply()
            current.copy(isLeftCharging = charging)
        } else {
            prefs?.edit()?.putBoolean(KEY_RIGHT_CHARGING, charging)?.apply()
            current.copy(isRightCharging = charging)
        }
        _earbudState.value = updated
    }

    fun setIndividualBudBattery(left: Int? = null, right: Int? = null) {
        val current = _earbudState.value
        _earbudState.value = current.copy(
            leftBattery = left ?: current.leftBattery,
            rightBattery = right ?: current.rightBattery
        )
    }

    fun refreshConnectedDevice() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _earbudState.value = _earbudState.value.copy(isConnected = false)
            return
        }

        try {
            val bonded = adapter.bondedDevices ?: emptySet()
            var connectedDevice: BluetoothDevice? = null

            for (dev in bonded) {
                if (isDeviceConnected(dev)) {
                    connectedDevice = dev
                    break
                }
            }

            if (connectedDevice == null) {
                connectedDevice = bonded.firstOrNull { dev ->
                    val devClass = dev.bluetoothClass?.deviceClass ?: 0
                    devClass == 0x0404 || devClass == 0x0418 || devClass == 0x0408 ||
                            dev.name?.contains("buds", ignoreCase = true) == true ||
                            dev.name?.contains("airpod", ignoreCase = true) == true ||
                            dev.name?.contains("tws", ignoreCase = true) == true ||
                            dev.name?.contains("trigor", ignoreCase = true) == true
                }
            }

            if (connectedDevice != null) {
                val battery = readBatteryLevel(connectedDevice)
                updateDevice(connectedDevice, battery)
                readMetadata(connectedDevice)
                readGattBattery(connectedDevice)
            } else if (bonded.isNotEmpty()) {
                val first = bonded.first()
                updateDevice(first, readBatteryLevel(first))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing connected device: ${e.message}")
        }
    }

    fun onAclConnected(device: BluetoothDevice) {
        val battery = readBatteryLevel(device)
        updateDevice(device, battery, isConnected = true)
        readMetadata(device)
        readGattBattery(device)
    }

    fun onAclDisconnected(device: BluetoothDevice) {
        if (_earbudState.value.address == device.address) {
            _earbudState.value = _earbudState.value.copy(isConnected = false)
        }
    }

    fun onBatteryChanged(device: BluetoothDevice, batteryLevel: Int) {
        val current = _earbudState.value
        if (current.address == device.address || current.address.isEmpty()) {
            val leftBat = if (batteryLevel >= 0) batteryLevel else current.leftBattery
            val rightBat = if (batteryLevel >= 0) batteryLevel else current.rightBattery

            _earbudState.value = current.copy(
                address = device.address,
                name = device.name ?: current.name,
                isConnected = true,
                leftBattery = leftBat,
                rightBattery = rightBat
            )
            readMetadata(device)
        }
    }

    private fun updateDevice(device: BluetoothDevice, batteryLevel: Int, isConnected: Boolean = true) {
        val name = device.name ?: "Wireless Earbuds"
        val current = _earbudState.value

        // Use reported battery for earbuds, keep distinct case battery
        val left = if (batteryLevel >= 0) batteryLevel else current.leftBattery
        val right = if (batteryLevel >= 0) batteryLevel else current.rightBattery
        val case = if (current.caseBattery > 0) current.caseBattery else 45

        _earbudState.value = current.copy(
            name = name,
            address = device.address,
            isConnected = isConnected,
            leftBattery = left,
            rightBattery = right,
            caseBattery = case
        )
    }

    private fun readMetadata(device: BluetoothDevice) {
        try {
            // AOSP Untethered Headset Metadata Keys:
            // 10: Left Battery, 11: Right Battery, 12: Case Battery
            // 13: Left Charging, 14: Right Charging, 15: Case Charging
            val leftBatStr = getMetadataString(device, 10)
            val rightBatStr = getMetadataString(device, 11)
            val caseBatStr = getMetadataString(device, 12)
            val leftChargeStr = getMetadataString(device, 13)
            val rightChargeStr = getMetadataString(device, 14)
            val caseChargeStr = getMetadataString(device, 15)

            var current = _earbudState.value

            leftBatStr?.toIntOrNull()?.let { current = current.copy(leftBattery = it) }
            rightBatStr?.toIntOrNull()?.let { current = current.copy(rightBattery = it) }
            caseBatStr?.toIntOrNull()?.let { current = current.copy(caseBattery = it) }

            leftChargeStr?.let { current = current.copy(isLeftCharging = it.equals("true", ignoreCase = true)) }
            rightChargeStr?.let { current = current.copy(isRightCharging = it.equals("true", ignoreCase = true)) }
            caseChargeStr?.let { current = current.copy(isCaseCharging = it.equals("true", ignoreCase = true)) }

            _earbudState.value = current
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading bluetooth metadata: ${e.message}")
        }
    }

    private fun getMetadataString(device: BluetoothDevice, key: Int): String? {
        return try {
            val method = BluetoothDevice::class.java.getMethod("getMetadata", Int::class.javaPrimitiveType ?: Int::class.java)
            val bytes = method.invoke(device, key) as? ByteArray
            bytes?.let { String(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun readBatteryLevel(device: BluetoothDevice): Int {
        return try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int ?: -1
            if (level >= 0) level else -1
        } catch (e: Exception) {
            -1
        }
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun readGattBattery(device: BluetoothDevice) {
        val ctx = appContext ?: return
        try {
            device.connectGatt(ctx, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt?.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gatt?.close()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                        val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                        val batteryChar = batteryService?.getCharacteristic(BATTERY_LEVEL_UUID)
                        if (batteryChar != null) {
                            gatt.readCharacteristic(batteryChar)
                        } else {
                            gatt.close()
                        }
                    }
                }

                override fun onCharacteristicRead(
                    gatt: BluetoothGatt?,
                    characteristic: BluetoothGattCharacteristic?,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                        if (characteristic.uuid == BATTERY_LEVEL_UUID) {
                            val level = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                            Log.d(TAG, "GATT battery level: $level%")
                            scope.launch {
                                onBatteryChanged(device, level)
                            }
                        }
                    }
                    gatt?.close()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "GATT connection failed: ${e.message}")
        }
    }
}
