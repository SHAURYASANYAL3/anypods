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
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.anypods.app.audio.AudioEnhancer
import me.anypods.app.diagnostics.DiagnosticManager
import me.anypods.app.model.EarbudState
import me.anypods.app.model.PodImageManager
import me.anypods.app.model.PodModelManager
import me.anypods.app.ui.OverlayPopup
import java.util.UUID

@SuppressLint("MissingPermission")
object BluetoothTracker {
    private const val TAG = "AnyPods.BluetoothTracker"
    private const val PREFS_NAME = "anypods_prefs"

    private const val KEY_LEFT_BATTERY = "left_battery"
    private const val KEY_RIGHT_BATTERY = "right_battery"
    private const val KEY_CASE_BATTERY = "case_battery"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_DEVICE_ADDRESS = "device_address"
    private const val KEY_SIGNAL_BOOST = "signal_boost_enabled"

    private val scope = CoroutineScope(Dispatchers.Main)

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothA2dp: BluetoothA2dp? = null
    private var bluetoothHeadset: BluetoothHeadset? = null
    private var bluetoothLeAudio: BluetoothProfile? = null
    private var bluetoothHearingAid: BluetoothProfile? = null

    private var bleScanner: BluetoothLeScanner? = null
    private var bleScanCallback: ScanCallback? = null
    private var isBleScanning = false
    private var lastPopupTime = 0L

    private var batteryPollingJob: Job? = null
    private var signalBoostKeepAliveJob: Job? = null

    private val _earbudState = MutableStateFlow(EarbudState())
    val earbudState: StateFlow<EarbudState> = _earbudState.asStateFlow()

    val signalBoostEnabled = MutableStateFlow(false)

    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    fun setSelectedModel(modelId: String) {
        _earbudState.value = _earbudState.value.copy(modelId = modelId)
        appContext?.let { ctx ->
            PodModelManager.saveSelectedModel(ctx, modelId)
        }
        AudioEnhancer.setTargetModel(modelId)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        // Restore last known device state so UI is never blank or waiting
        val savedModel = PodModelManager.getSelectedModel(context)
        val savedName = sp.getString(KEY_DEVICE_NAME, "Wireless Earbuds") ?: "Wireless Earbuds"
        val savedAddr = sp.getString(KEY_DEVICE_ADDRESS, "") ?: ""
        val savedLeft = sp.getInt(KEY_LEFT_BATTERY, 100)
        val savedRight = sp.getInt(KEY_RIGHT_BATTERY, 100)
        val savedCase = sp.getInt(KEY_CASE_BATTERY, 80)
        _earbudState.value = EarbudState(
            name = savedName,
            address = savedAddr,
            modelId = savedModel.id,
            leftBattery = savedLeft,
            rightBattery = savedRight,
            caseBattery = savedCase
        )

        signalBoostEnabled.value = sp.getBoolean(KEY_SIGNAL_BOOST, false)
        if (signalBoostEnabled.value) {
            startSignalBoostKeepAlive()
        }

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
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.A2DP) bluetoothA2dp = null
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

            // Bind LE Audio (Profile 22) on Android 12+ (API 31+)
            try {
                adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        bluetoothLeAudio = proxy
                        Log.d(TAG, "Bluetooth LE Audio proxy connected")
                        refreshConnectedDevice()
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        bluetoothLeAudio = null
                    }
                }, 22)
            } catch (_: Exception) {}

            // Bind Hearing Aid (Profile 21) on Android 10+ (API 29+)
            try {
                adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        bluetoothHearingAid = proxy
                        Log.d(TAG, "Bluetooth Hearing Aid proxy connected")
                        refreshConnectedDevice()
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        bluetoothHearingAid = null
                    }
                }, 21)
            } catch (_: Exception) {}
        }

        refreshConnectedDevice()
        startPeriodicBatteryPolling()
        startAppleBeaconScanning()
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

    fun startAppleBeaconScanning() {
        val ctx = appContext ?: return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || isBleScanning) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_SCAN permission not granted; skipping BLE beacon scanner")
                return
            }
        }

        try {
            bleScanner = adapter.bluetoothLeScanner
            if (bleScanner == null) return

            // Scan for Apple manufacturer data (ID 76 = 0x004C) with proximity prefix 0x07, 0x19
            val scanFilter = ScanFilter.Builder()
                .setManufacturerData(
                    AppleBeaconParser.APPLE_MANUFACTURER_ID,
                    byteArrayOf(7, 25),
                    byteArrayOf(-1, -1)
                )
                .build()

            val scanMode = if (signalBoostEnabled.value) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED
            val settingsBuilder = ScanSettings.Builder().setScanMode(scanMode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && signalBoostEnabled.value) {
                settingsBuilder.setLegacy(false)
                settingsBuilder.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            }
            val scanSettings = settingsBuilder.build()

            bleScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.let { handleScanResult(it) }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    results?.forEach { handleScanResult(it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w(TAG, "Apple BLE scan failed with error code $errorCode")
                    isBleScanning = false
                }
            }

            bleScanner?.startScan(listOf(scanFilter), scanSettings, bleScanCallback)
            isBleScanning = true
            Log.i(TAG, "Apple AirPods BLE Beacon scanner started successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start Apple BLE Beacon scanner: ${e.message}")
        }
    }

    fun stopAppleBeaconScanning() {
        if (!isBleScanning) return
        try {
            bleScanCallback?.let { bleScanner?.stopScan(it) }
        } catch (_: Exception) {}
        bleScanCallback = null
        bleScanner = null
        isBleScanning = false
        Log.i(TAG, "Apple AirPods BLE Beacon scanner stopped")
    }

    private fun handleScanResult(result: ScanResult) {
        val current = _earbudState.value
        // Only process Apple AirPods proximity beacons if connected to Apple gear or not connected to non-Apple earbuds
        val isNonAppleConnected = current.isConnected && !current.modelId.startsWith("airpods") && !current.name.lowercase().contains("airpod")
        if (isNonAppleConnected) {
            return
        }

        val scanRecord = result.scanRecord ?: return
        val rawData = scanRecord.getManufacturerSpecificData(AppleBeaconParser.APPLE_MANUFACTURER_ID) ?: return
        val beacon = AppleBeaconParser.parse(rawData) ?: return

        val rssi = result.rssi

        val effectiveLeft = if (beacon.leftBattery in 0..100) beacon.leftBattery else current.leftBattery
        val effectiveRight = if (beacon.rightBattery in 0..100) beacon.rightBattery else current.rightBattery
        val effectiveCase = if (beacon.caseBattery in 0..100) beacon.caseBattery else current.caseBattery

        val wasCharging = current.isLeftCharging || current.isRightCharging || current.isCaseCharging
        val isNowCharging = beacon.isLeftCharging || beacon.isRightCharging || beacon.isCaseCharging

        val updatedState = current.copy(
            modelId = beacon.modelId,
            leftBattery = effectiveLeft,
            rightBattery = effectiveRight,
            caseBattery = effectiveCase,
            isLeftCharging = beacon.isLeftCharging,
            isRightCharging = beacon.isRightCharging,
            isCaseCharging = beacon.isCaseCharging,
            isLeftInEar = beacon.isLeftInEar,
            isRightInEar = beacon.isRightInEar
        )
        _earbudState.value = updatedState

        prefs?.edit()
            ?.putInt(KEY_LEFT_BATTERY, effectiveLeft)
            ?.putInt(KEY_RIGHT_BATTERY, effectiveRight)
            ?.putInt(KEY_CASE_BATTERY, effectiveCase)
            ?.apply()

        // Sync active model with PodModelManager and AudioEnhancer
        appContext?.let { ctx ->
            PodModelManager.saveSelectedModel(ctx, beacon.modelId)
        }
        AudioEnhancer.setTargetModel(beacon.modelId)
        DiagnosticManager.logEvent("AppleBeacon", "model=${beacon.modelId}, L=${effectiveLeft}%⚡${beacon.isLeftCharging}, R=${effectiveRight}%⚡${beacon.isRightCharging}, Case=${effectiveCase}%⚡${beacon.isCaseCharging}, inEar(L=${beacon.isLeftInEar}, R=${beacon.isRightInEar})")

        // Trigger AirPods popup card when case lid is opened nearby
        val now = System.currentTimeMillis()
        if (rssi > -72 && (beacon.caseBattery > 0 || isNowCharging != wasCharging) && (now - lastPopupTime > 12000L)) {
            lastPopupTime = now
            appContext?.let { ctx ->
                OverlayPopup.show(ctx, updatedState)
            }
        }
    }

    fun refreshConnectedDevice() {
        val ctx = appContext ?: return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _earbudState.value = _earbudState.value.copy(isConnected = false)
            stopAppleBeaconScanning()
            return
        }

        startAppleBeaconScanning()

        try {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val connectedDevice = findConnectedAudioDevice(audioManager, adapter)

            if (connectedDevice != null) {
                val hwBattery = readBatteryLevel(connectedDevice)
                updateConnectedDevice(connectedDevice, hwBattery)
                readMetadata(connectedDevice)
                readGattBattery(connectedDevice)
                AirPodsL2capClient.connect(connectedDevice)
                if (signalBoostEnabled.value) {
                    boostConnectedGattLink(connectedDevice)
                }
            } else {
                // Only mark isConnected = false if Bluetooth proxies have had a chance to connect
                val proxiesReady = (bluetoothA2dp != null || bluetoothHeadset != null)
                val current = _earbudState.value
                if (proxiesReady && current.isConnected) {
                    _earbudState.value = current.copy(isConnected = false)
                    Log.d(TAG, "No Bluetooth audio device connected. Updated isConnected=false")
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

        // 3. Try LE Audio proxy (Android 12+)
        try {
            bluetoothLeAudio?.connectedDevices?.firstOrNull()?.let { return it }
        } catch (_: Exception) {}

        // 4. Try Hearing Aid proxy (Android 10+)
        try {
            bluetoothHearingAid?.connectedDevices?.firstOrNull()?.let { return it }
        } catch (_: Exception) {}

        // 5. Try AudioManager AudioDeviceInfo (Android 6.0+)
        if (audioManager != null) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (info in devices) {
                if (info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.type == AudioDeviceInfo.TYPE_HEARING_AID)
                ) {
                    val address = info.address
                    if (!address.isNullOrEmpty()) {
                        adapter.bondedDevices?.firstOrNull { it.address.equals(address, ignoreCase = true) }?.let { return it }
                        try {
                            val dev = adapter.getRemoteDevice(address)
                            if (dev != null) return dev
                        } catch (_: Exception) {}
                    }
                    val pName = info.productName.toString()
                    adapter.bondedDevices?.firstOrNull { it.name.equals(pName, ignoreCase = true) }?.let { return it }
                }
            }
        }

        // 6. Try reflection isConnected on bonded devices - ONLY for audio devices (filters out watches, bands, keyboards)
        return adapter.bondedDevices?.firstOrNull { isAudioDevice(it) && isDeviceConnected(it) }
    }

    fun isAudioDevice(device: BluetoothDevice): Boolean {
        try {
            val btClass = device.bluetoothClass ?: return true
            val majorClass = btClass.majorDeviceClass
            if (majorClass == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO) {
                return true
            }
            val devClass = btClass.deviceClass
            val audioClasses = setOf(
                android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
                android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
                android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
                android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
                android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
                android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO
            )
            if (devClass in audioClasses) return true

            // Ignore smartwatches, wearables, fitness bands, computers, phones, health devices
            if (majorClass == android.bluetooth.BluetoothClass.Device.Major.WEARABLE ||
                majorClass == android.bluetooth.BluetoothClass.Device.Major.COMPUTER ||
                majorClass == android.bluetooth.BluetoothClass.Device.Major.PHONE ||
                majorClass == android.bluetooth.BluetoothClass.Device.Major.HEALTH
            ) {
                return false
            }
        } catch (_: Exception) {}
        return true
    }

    private fun updateConnectedDevice(device: BluetoothDevice, hwBattery: Int) {
        val name = device.name ?: "Wireless Earbuds"
        val detectedModel = PodModelManager.detectModel(name)
        val current = _earbudState.value
        val isDifferentDevice = current.address != device.address || !current.isConnected

        val effectiveBattery = if (hwBattery in 0..100) {
            hwBattery
        } else if (!isDifferentDevice && current.leftBattery in 1..100) {
            current.leftBattery
        } else {
            prefs?.getInt(KEY_LEFT_BATTERY, 85) ?: 85
        }

        val caseBat = if (current.caseBattery in 1..100) current.caseBattery else 80

        val savedModelId = prefs?.getString(PodModelManager.KEY_SELECTED_MODEL, null)
        val modelToUse = if (isDifferentDevice) {
            detectedModel.id
        } else if (!savedModelId.isNullOrEmpty()) {
            savedModelId
        } else if (current.modelId.isNotEmpty()) {
            current.modelId
        } else {
            detectedModel.id
        }

        _earbudState.value = current.copy(
            name = name,
            address = device.address,
            isConnected = true,
            modelId = modelToUse,
            leftBattery = effectiveBattery,
            rightBattery = effectiveBattery,
            caseBattery = caseBat,
            isLeftCharging = current.isLeftCharging,
            isRightCharging = current.isRightCharging,
            isCaseCharging = current.isCaseCharging
        )

        prefs?.edit()
            ?.putString(KEY_DEVICE_NAME, name)
            ?.putString(KEY_DEVICE_ADDRESS, device.address)
            ?.putInt(KEY_LEFT_BATTERY, effectiveBattery)
            ?.putInt(KEY_RIGHT_BATTERY, effectiveBattery)
            ?.apply()

        appContext?.let { ctx ->
            PodModelManager.saveSelectedModel(ctx, modelToUse)
            PodImageManager.autoFetchIfMissing(ctx, name)
        }
        AudioEnhancer.setTargetModel(modelToUse)
        DiagnosticManager.logEvent("Bluetooth", "Connected: $name [${DiagnosticManager.maskAddress(device.address)}], model=$modelToUse, battery=$effectiveBattery%")
    }

    fun onAclConnected(device: BluetoothDevice) {
        val hwBattery = readBatteryLevel(device)
        updateConnectedDevice(device, hwBattery)
        readMetadata(device)
        readGattBattery(device)
        startAppleBeaconScanning()
        AirPodsL2capClient.connect(device)
    }

    fun onAclDisconnected(device: BluetoothDevice) {
        if (_earbudState.value.address == device.address) {
            _earbudState.value = _earbudState.value.copy(isConnected = false)
            DiagnosticManager.logEvent("Bluetooth", "Disconnected: ${device.name ?: "Unknown"}")
            AirPodsL2capClient.disconnect()
        }
    }

    /**
     * Called automatically when Android OS or GATT broadcast reports battery change.
     */
    fun onBatteryChanged(device: BluetoothDevice, batteryLevel: Int) {
        if (batteryLevel !in 0..100) return
        val current = _earbudState.value
        val name = device.name ?: current.name
        val isDifferentDevice = current.address != device.address || !current.isConnected
        val detectedModel = PodModelManager.detectModel(name)
        val modelToUse = if (isDifferentDevice) {
            detectedModel.id
        } else if (current.modelId.isNotEmpty()) {
            current.modelId
        } else {
            detectedModel.id
        }

        Log.i(TAG, "Auto-detected real battery level: $batteryLevel% for $name")
        DiagnosticManager.logEvent("Battery", "Reported battery: $batteryLevel% for $name")

        _earbudState.value = current.copy(
            address = device.address,
            name = name,
            isConnected = true,
            modelId = modelToUse,
            leftBattery = batteryLevel,
            rightBattery = batteryLevel,
            isLeftCharging = false,
            isRightCharging = false
        )

        prefs?.edit()?.putInt(KEY_LEFT_BATTERY, batteryLevel)?.putInt(KEY_RIGHT_BATTERY, batteryLevel)?.apply()
        appContext?.let { ctx ->
            PodModelManager.saveSelectedModel(ctx, modelToUse)
        }
        AudioEnhancer.setTargetModel(modelToUse)
        readMetadata(device)
    }

    /**
     * Called when HFP (+IPHONEACCEV) or vendor telemetry reports separate Left/Right/Case status.
     */
    fun onDualBatteryChanged(
        device: BluetoothDevice,
        leftBattery: Int,
        rightBattery: Int,
        caseBattery: Int = -1,
        isLeftCharging: Boolean = false,
        isRightCharging: Boolean = false,
        isCaseCharging: Boolean = false
    ) {
        val current = _earbudState.value
        val name = device.name ?: current.name
        val isDifferentDevice = current.address != device.address || !current.isConnected
        val detectedModel = PodModelManager.detectModel(name)
        val modelToUse = if (isDifferentDevice) {
            detectedModel.id
        } else if (current.modelId.isNotEmpty()) {
            current.modelId
        } else {
            detectedModel.id
        }

        val effectiveLeft = if (leftBattery in 0..100) leftBattery else current.leftBattery
        val effectiveRight = if (rightBattery in 0..100) rightBattery else current.rightBattery
        val effectiveCase = if (caseBattery in 0..100) caseBattery else current.caseBattery

        _earbudState.value = current.copy(
            address = device.address,
            name = name,
            isConnected = true,
            modelId = modelToUse,
            leftBattery = effectiveLeft,
            rightBattery = effectiveRight,
            caseBattery = effectiveCase,
            isLeftCharging = isLeftCharging,
            isRightCharging = isRightCharging,
            isCaseCharging = isCaseCharging
        )
        prefs?.edit()
            ?.putInt(KEY_LEFT_BATTERY, effectiveLeft)
            ?.putInt(KEY_RIGHT_BATTERY, effectiveRight)
            ?.putInt(KEY_CASE_BATTERY, effectiveCase)
            ?.apply()

        appContext?.let { ctx ->
            PodModelManager.saveSelectedModel(ctx, modelToUse)
        }
        AudioEnhancer.setTargetModel(modelToUse)
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

    fun setInEarStatus(leftInEar: Boolean, rightInEar: Boolean) {
        _earbudState.value = _earbudState.value.copy(
            isLeftInEar = leftInEar,
            isRightInEar = rightInEar
        )
    }

    fun updateBatteryFromL2cap(
        leftLevel: Int,
        leftCharging: Boolean,
        rightLevel: Int,
        rightCharging: Boolean,
        caseLevel: Int,
        caseCharging: Boolean
    ) {
        val cur = _earbudState.value
        val effLeft = if (leftLevel >= 0) leftLevel else cur.leftBattery
        val effRight = if (rightLevel >= 0) rightLevel else cur.rightBattery
        val effCase = if (caseLevel >= 0) caseLevel else cur.caseBattery
        _earbudState.value = cur.copy(
            leftBattery = effLeft,
            isLeftCharging = leftCharging,
            rightBattery = effRight,
            isRightCharging = rightCharging,
            caseBattery = effCase,
            isCaseCharging = caseCharging
        )
        prefs?.edit()
            ?.putInt(KEY_LEFT_BATTERY, effLeft)
            ?.putInt(KEY_RIGHT_BATTERY, effRight)
            ?.putInt(KEY_CASE_BATTERY, effCase)
            ?.apply()
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
        // Direct L2CAP channel (PSM 0x1001) dispatch for AirPods / Beats
        AirPodsL2capClient.sendNoiseControl(mode)

        val dev = getConnectedDevice() ?: return
        val modeVal = when (mode) {
            me.anypods.app.anc.NoiseMode.NOISE_CANCELLATION -> 0x02.toByte()
            me.anypods.app.anc.NoiseMode.TRANSPARENCY -> 0x03.toByte()
            me.anypods.app.anc.NoiseMode.OFF -> 0x01.toByte()
            me.anypods.app.anc.NoiseMode.ADAPTIVE -> 0x04.toByte()
        }

        // 1. Dispatch FastPair / Android System Bluetooth Metadata keys 17 and 25
        try {
            val method = dev.javaClass.getMethod(
                "setMetadata",
                Int::class.javaPrimitiveType ?: Int::class.java,
                ByteArray::class.java
            )
            method.invoke(dev, 17, byteArrayOf(modeVal))
            try { method.invoke(dev, 25, byteArrayOf(modeVal)) } catch (_: Exception) {}
            Log.d(TAG, "Dispatched setMetadata listening mode $mode to ${dev.name}")
        } catch (e: Exception) {
            Log.d(TAG, "setMetadata listening mode unavailable on this ROM: ${e.message}")
        }

        // 2. Direct RFCOMM fallback if L2CAP is disconnected
        if (!AirPodsL2capClient.isConnected) {
            val aacpPayload = byteArrayOf(
                0x04, 0x00, 0x04, 0x00,
                0x09, 0x00,
                0x0D,
                modeVal,
                0x00, 0x00, 0x00
            )
            scope.launch(Dispatchers.IO) {
                var rfcommSocket: android.bluetooth.BluetoothSocket? = null
                try {
                    val appleUuid = UUID.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")
                    rfcommSocket = dev.createInsecureRfcommSocketToServiceRecord(appleUuid)
                    rfcommSocket?.let { s ->
                        s.connect()
                        val out = s.outputStream
                        out.write(aacpPayload)
                        out.flush()
                        Log.d(TAG, "Sent AACP listening mode $modeVal over RFCOMM fallback to ${dev.name}")
                        s.close()
                    }
                } catch (e: Exception) {
                    try { rfcommSocket?.close() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun readGattBattery(device: BluetoothDevice) {
        val ctx = appContext ?: return
        try {
            var gattRef: BluetoothGatt? = null
            var isClosed = false
            val closeLock = Any()

            fun safeClose() {
                synchronized(closeLock) {
                    if (!isClosed) {
                        isClosed = true
                        try { gattRef?.close() } catch (_: Exception) {}
                        gattRef = null
                    }
                }
            }

            // 5-second timeout ensures GATT client connection slots (max 32) are never leaked
            val timeoutJob = scope.launch(Dispatchers.Default) {
                delay(5000)
                safeClose()
            }

            gattRef = device.connectGatt(ctx, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        timeoutJob.cancel()
                        safeClose()
                        return
                    }
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt?.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        timeoutJob.cancel()
                        safeClose()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                        val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                        val batteryChar = batteryService?.getCharacteristic(BATTERY_LEVEL_UUID)
                        if (batteryChar != null) {
                            gatt.readCharacteristic(batteryChar)
                        } else {
                            timeoutJob.cancel()
                            safeClose()
                        }
                    } else {
                        timeoutJob.cancel()
                        safeClose()
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
                    timeoutJob.cancel()
                    safeClose()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "GATT connection failed: ${e.message}")
        }
    }

    /**
     * Toggles Long-Range Signal Boost mode:
     * - Configures LE Coded PHY (S=8) for 4x RF link margin & wall penetration on Bluetooth 5.0+
     * - Sets GATT priority to HIGH (low-latency connection intervals)
     * - Runs high-power scan mode and periodic keepalive pings to prevent link sleep at extreme distances
     */
    fun toggleSignalBoost(context: Context, enabled: Boolean) {
        signalBoostEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_SIGNAL_BOOST, enabled)?.apply()

        // Restart BLE scanner with high-power RF profile
        stopAppleBeaconScanning()
        startAppleBeaconScanning()

        if (enabled) {
            startSignalBoostKeepAlive()
            val current = _earbudState.value
            if (current.isConnected && current.address.isNotBlank()) {
                val adapter = bluetoothAdapter
                if (adapter != null) {
                    try {
                        val dev = adapter.getRemoteDevice(current.address)
                        if (dev != null) boostConnectedGattLink(dev)
                    } catch (_: Exception) {}
                }
            }
        } else {
            signalBoostKeepAliveJob?.cancel()
            signalBoostKeepAliveJob = null
        }
        Log.i(TAG, "Signal Boost toggled: $enabled")
    }

    private fun startSignalBoostKeepAlive() {
        signalBoostKeepAliveJob?.cancel()
        signalBoostKeepAliveJob = scope.launch(Dispatchers.IO) {
            while (isActive && signalBoostEnabled.value) {
                delay(4000)
                if (AirPodsL2capClient.isConnected) {
                    AirPodsL2capClient.sendKeepAlivePing()
                }
            }
        }
    }

    private fun boostConnectedGattLink(device: BluetoothDevice) {
        val ctx = appContext ?: return
        try {
            device.connectGatt(ctx, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "Signal Boost: Requesting HIGH connection priority")
                        gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Log.d(TAG, "Signal Boost: Requesting LE Coded PHY (S=8 Long Range)")
                            gatt?.setPreferredPhy(
                                BluetoothDevice.PHY_LE_CODED_MASK,
                                BluetoothDevice.PHY_LE_CODED_MASK,
                                BluetoothDevice.PHY_OPTION_S8
                            )
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply GATT signal boost: ${e.message}")
        }
    }
}
