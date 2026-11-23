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

    private const val KEY_LEFT_BATTERY = "left_battery"
    private const val KEY_RIGHT_BATTERY = "right_battery"
    private const val KEY_CASE_BATTERY = "case_battery"
    private const val KEY_LEFT_CHARGING = "left_charging"
    private const val KEY_RIGHT_CHARGING = "right_charging"
    private const val KEY_CASE_CHARGING = "case_charging"

    private val scope = CoroutineScope(Dispatchers.Main)

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val _earbudState = MutableStateFlow(EarbudState())
    val earbudState: StateFlow<EarbudState> = _earbudState.asStateFlow()

    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    fun init(context: Context) {
        appContext = context.applicationContext
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val savedLeft = sp.getInt(KEY_LEFT_BATTERY, 100)
        val savedRight = sp.getInt(KEY_RIGHT_BATTERY, 100)
        val savedCase = sp.getInt(KEY_CASE_BATTERY, 45)

        val savedLeftCharging = sp.getBoolean(KEY_LEFT_CHARGING, true)
        val savedRightCharging = sp.getBoolean(KEY_RIGHT_CHARGING, false)
        val savedCaseCharging = sp.getBoolean(KEY_CASE_CHARGING, false)

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bm?.adapter

        _earbudState.value = _earbudState.value.copy(
            leftBattery = savedLeft,
            rightBattery = savedRight,
            caseBattery = savedCase,
            isLeftCharging = savedLeftCharging,
            isRightCharging = savedRightCharging,
            isCaseCharging = savedCaseCharging
        )

        refreshConnectedDevice()
    }

    fun setLeftPod(level: Int, isCharging: Boolean) {
        val clamped = level.coerceIn(0, 100)
        prefs?.edit()?.putInt(KEY_LEFT_BATTERY, clamped)?.putBoolean(KEY_LEFT_CHARGING, isCharging)?.apply()
        _earbudState.value = _earbudState.value.copy(
            leftBattery = clamped,
            isLeftCharging = isCharging
        )
    }

    fun setRightPod(level: Int, isCharging: Boolean) {
        val clamped = level.coerceIn(0, 100)
        prefs?.edit()?.putInt(KEY_RIGHT_BATTERY, clamped)?.putBoolean(KEY_RIGHT_CHARGING, isCharging)?.apply()
        _earbudState.value = _earbudState.value.copy(
            rightBattery = clamped,
            isRightCharging = isCharging
        )
    }

    fun setCase(level: Int, isCharging: Boolean) {
        val clamped = level.coerceIn(0, 100)
        prefs?.edit()?.putInt(KEY_CASE_BATTERY, clamped)?.putBoolean(KEY_CASE_CHARGING, isCharging)?.apply()
        _earbudState.value = _earbudState.value.copy(
            caseBattery = clamped,
            isCaseCharging = isCharging
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
                val hwBattery = readBatteryLevel(connectedDevice)
                updateConnectedDevice(connectedDevice, hwBattery)
                readMetadata(connectedDevice)
                readGattBattery(connectedDevice)
            } else if (bonded.isNotEmpty()) {
                val first = bonded.first()
                updateConnectedDevice(first, readBatteryLevel(first))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing connected device: ${e.message}")
        }
    }

    private fun updateConnectedDevice(device: BluetoothDevice, hwBattery: Int) {
        val name = device.name ?: "Wireless Earbuds"
        val current = _earbudState.value

        // Only override earbud battery if none was saved yet
        val left = if (current.leftBattery > 0) current.leftBattery else (if (hwBattery >= 0) hwBattery else 100)
        val right = if (current.rightBattery > 0) current.rightBattery else (if (hwBattery >= 0) hwBattery else 100)
        val case = if (current.caseBattery > 0) current.caseBattery else 45

        _earbudState.value = current.copy(
            name = name,
            address = device.address,
            isConnected = true,
            leftBattery = left,
            rightBattery = right,
            caseBattery = case
        )
    }

    fun onAclConnected(device: BluetoothDevice) {
        val hwBattery = readBatteryLevel(device)
        updateConnectedDevice(device, hwBattery)
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
            _earbudState.value = current.copy(
                address = device.address,
                name = device.name ?: current.name,
                isConnected = true
            )
            readMetadata(device)
        }
    }

    private fun readMetadata(device: BluetoothDevice) {
        try {
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
