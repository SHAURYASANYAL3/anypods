package me.anypods.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.anypods.app.model.EarbudState
import java.util.UUID

@SuppressLint("MissingPermission")
object BluetoothTracker {
    private const val TAG = "AnyPods.BluetoothTracker"
    private const val PREFS_NAME = "anypods_prefs"

    private const val KEY_LEFT_BATTERY = "left_battery"
    private const val KEY_RIGHT_BATTERY = "right_battery"
    private const val KEY_CASE_BATTERY = "case_battery"

    private val scope = CoroutineScope(Dispatchers.Main)

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothA2dp: BluetoothA2dp? = null
    private var bluetoothHeadset: BluetoothHeadset? = null

    private var batteryPollingJob: Job? = null

    private val _earbudState = MutableStateFlow(EarbudState())
    val earbudState: StateFlow<EarbudState> = _earbudState.asStateFlow()

    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    fun init(context: Context) {
        appContext = context.applicationContext
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bm?.adapter

        // Bind Bluetooth Profile Proxies (A2DP and Headset) to detect connected audio gear accurately
        bluetoothAdapter?.let { adapter ->
            adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.A2DP) {
                        bluetoothA2dp = proxy as? BluetoothA2dp
                        Log.d(TAG, "BluetoothA2dp proxy connected")
                        refreshConnectedDevice()
                    } else if (profile == BluetoothProfile.HEADSET) {
                        bluetoothHeadset = proxy as? BluetoothHeadset
                        Log.d(TAG, "BluetoothHeadset proxy connected")
                        refreshConnectedDevice()
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.A2DP) bluetoothA2dp = null
                    if (profile == BluetoothProfile.HEADSET) bluetoothHeadset = null
                }
            }, BluetoothProfile.A2DP)

            adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.HEADSET) {
                        bluetoothHeadset = proxy as? BluetoothHeadset
                        Log.d(TAG, "BluetoothHeadset proxy connected")
                        refreshConnectedDevice()
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HEADSET) bluetoothHeadset = null
                }
            }, BluetoothProfile.HEADSET)
        }

        refreshConnectedDevice()
        startPeriodicBatteryPolling()
    }

    private fun startPeriodicBatteryPolling() {
        batteryPollingJob?.cancel()
        batteryPollingJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(8000)
                withContext(Dispatchers.Main) {
                    refreshConnectedDevice()
                }
            }
        }
    }

    fun refreshConnectedDevice() {
        val ctx = appContext ?: return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _earbudState.value = _earbudState.value.copy(isConnected = false)
            return
        }

        try {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val connectedDevice = findConnectedAudioDevice(audioManager, adapter)

            if (connectedDevice != null) {
                val hwBattery = readBatteryLevel(connectedDevice)
                updateConnectedDevice(connectedDevice, hwBattery)
                readMetadata(connectedDevice)
                readGattBattery(connectedDevice)
            } else {
                val bonded = adapter.bondedDevices ?: emptySet()
                if (bonded.isNotEmpty()) {
                    val first = bonded.first()
                    updateConnectedDevice(first, readBatteryLevel(first))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing connected device: ${e.message}")
        }
    }

    private fun findConnectedAudioDevice(audioManager: AudioManager?, adapter: BluetoothAdapter): BluetoothDevice? {
        // 1. Try A2DP proxy
        bluetoothA2dp?.connectedDevices?.firstOrNull()?.let { return it }

        // 2. Try Headset proxy
        bluetoothHeadset?.connectedDevices?.firstOrNull()?.let { return it }

        // 3. Try AudioManager AudioDeviceInfo (Android 6.0+)
        if (audioManager != null) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (info in devices) {
                if (info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                ) {
                    val address = info.address
                    if (!address.isNullOrEmpty()) {
                        adapter.bondedDevices?.firstOrNull { it.address.equals(address, ignoreCase = true) }?.let { return it }
                    }
                    val pName = info.productName.toString()
                    adapter.bondedDevices?.firstOrNull { it.name.equals(pName, ignoreCase = true) }?.let { return it }
                }
            }
        }

        // 4. Try reflection isConnected on bonded devices
        return adapter.bondedDevices?.firstOrNull { isDeviceConnected(it) }
    }

    private fun updateConnectedDevice(device: BluetoothDevice, hwBattery: Int) {
        val name = device.name ?: "Wireless Earbuds"
        val current = _earbudState.value

        // Automatically set pods battery from hardware report if available
        val effectiveBattery = if (hwBattery in 0..100) {
            hwBattery
        } else if (current.leftBattery in 1..100) {
            current.leftBattery
        } else {
            prefs?.getInt(KEY_LEFT_BATTERY, 85) ?: 85
        }

        val caseBat = if (current.caseBattery in 1..100) current.caseBattery else 80

        // When earbuds are connected and in active use, they are in the user's ears (discharging)
        _earbudState.value = current.copy(
            name = name,
            address = device.address,
            isConnected = true,
            leftBattery = effectiveBattery,
            rightBattery = effectiveBattery,
            caseBattery = caseBat,
            isLeftCharging = false,
            isRightCharging = false,
            isCaseCharging = current.isCaseCharging
        )

        // Automatically fetch and cache real product photo from the internet
        appContext?.let { ctx ->
            me.anypods.app.model.PodImageManager.autoFetchIfMissing(ctx, name)
        }
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

    /**
     * Called automatically when Android OS or GATT broadcast reports battery change.
     */
    fun onBatteryChanged(device: BluetoothDevice, batteryLevel: Int) {
        if (batteryLevel !in 0..100) return
        val current = _earbudState.value
        Log.i(TAG, "Auto-detected real battery level: $batteryLevel% for ${device.name}")

        _earbudState.value = current.copy(
            address = device.address,
            name = device.name ?: current.name,
            isConnected = true,
            leftBattery = batteryLevel,
            rightBattery = batteryLevel,
            isLeftCharging = false,
            isRightCharging = false
        )

        prefs?.edit()?.putInt(KEY_LEFT_BATTERY, batteryLevel)?.putInt(KEY_RIGHT_BATTERY, batteryLevel)?.apply()
        readMetadata(device)
    }

    fun setLeftPod(level: Int, isCharging: Boolean) {
        _earbudState.value = _earbudState.value.copy(leftBattery = level.coerceIn(0, 100), isLeftCharging = isCharging)
    }

    fun setRightPod(level: Int, isCharging: Boolean) {
        _earbudState.value = _earbudState.value.copy(rightBattery = level.coerceIn(0, 100), isRightCharging = isCharging)
    }

    fun setCase(level: Int, isCharging: Boolean) {
        _earbudState.value = _earbudState.value.copy(caseBattery = level.coerceIn(0, 100), isCaseCharging = isCharging)
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

            leftBatStr?.toIntOrNull()?.let { current = current.copy(leftBattery = it.coerceIn(0, 100)) }
            rightBatStr?.toIntOrNull()?.let { current = current.copy(rightBattery = it.coerceIn(0, 100)) }
            caseBatStr?.toIntOrNull()?.let { current = current.copy(caseBattery = it.coerceIn(0, 100)) }

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
        } catch (_: Exception) {
            null
        }
    }

    private fun readBatteryLevel(device: BluetoothDevice): Int {
        return try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int ?: -1
            if (level in 0..100) level else -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun getConnectedDevice(): BluetoothDevice? {
        val adapter = bluetoothAdapter ?: return null
        val ctx = appContext ?: return null
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return findConnectedAudioDevice(audioManager, adapter)
    }

    fun sendHardwareListeningMode(mode: me.anypods.app.anc.NoiseMode) {
        try {
            val dev = getConnectedDevice() ?: return
            val method = dev.javaClass.getMethod(
                "setMetadata",
                Int::class.javaPrimitiveType ?: Int::class.java,
                ByteArray::class.java
            )
            val modeVal = when (mode) {
                me.anypods.app.anc.NoiseMode.NOISE_CANCELLATION -> byteArrayOf(0x02)
                me.anypods.app.anc.NoiseMode.TRANSPARENCY -> byteArrayOf(0x03)
                me.anypods.app.anc.NoiseMode.OFF -> byteArrayOf(0x01)
                me.anypods.app.anc.NoiseMode.ADAPTIVE -> byteArrayOf(0x04)
            }
            method.invoke(dev, 17, modeVal)
            Log.d(TAG, "Dispatched hardware listening mode $mode to ${dev.name}")
        } catch (_: Exception) {}
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
