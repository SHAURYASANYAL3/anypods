package me.anypods.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*
import me.anypods.app.anc.NoiseMode
import java.io.InputStream
import java.io.OutputStream

/**
 * Native Android L2CAP Client for Apple AirPods / Beats protocol (PSM 0x1001).
 * Handles hardware ANC/Transparency mode switching, conversation awareness,
 * stem swipe gestures, in-ear detection, and unencrypted battery state updates.
 *
 * // ponytail: Standard Android L2CAP socket (PSM 4097) with constructor reflection fallback.
 * // Zero NDK/Rust overhead; handles bidirectional control and event streaming in <180 lines.
 */
@SuppressLint("MissingPermission")
object AirPodsL2capClient {
    private const val TAG = "AnyPods.L2capClient"
    const val PSM_APPLE_CONTROL = 0x1001 // 4097 decimal

    // Standard Apple Accessory Communication Protocol (AACP) opcodes & headers
    private val HEADER_CONTROL = byteArrayOf(0x04, 0x00, 0x04, 0x00)
    private val CMD_CONTROL = byteArrayOf(0x09, 0x00)

    // Handshake sequence packets
    val PACKET_HANDSHAKE = byteArrayOf(
        0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )
    val PACKET_SET_FEATURES = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x4D.toByte(), 0x00, 0xD7.toByte(), 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )
    val PACKET_REQUEST_NOTIFICATIONS = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x0F, 0x00,
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
    )

    // Command Identifiers
    const val ID_LISTENING_MODE: Byte = 0x0D
    const val ID_ONE_BUD_ANC: Byte = 0x1B
    const val ID_VOLUME_SWIPE: Byte = 0x25
    const val ID_ADAPTIVE_VOLUME: Byte = 0x26
    const val ID_CONVERSATION_AWARENESS: Byte = 0x28

    // Notification Opcodes
    const val OPCODE_BATTERY_STATUS: Byte = 0x04
    const val OPCODE_EAR_DETECTION: Byte = 0x06
    const val OPCODE_CONTROL_COMMAND: Byte = 0x09

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var readJob: Job? = null
    private var connectedAddress: String? = null

    val isConnected: Boolean
        get() = activeSocket?.isConnected == true

    /**
     * Connects to Apple AirPods L2CAP control channel (PSM 0x1001), runs the
     * 3-step initialization handshake, and begins reading live hardware notifications.
     */
    fun connect(device: BluetoothDevice) {
        if (isConnected && connectedAddress == device.address) {
            Log.d(TAG, "L2CAP client already active for ${device.name ?: device.address}")
            return
        }

        disconnect()

        scope.launch {
            try {
                Log.d(TAG, "Initiating L2CAP channel (PSM 0x1001) connection to ${device.name ?: device.address}...")
                val socket = createL2capSocket(device)
                socket.connect()

                activeSocket = socket
                outputStream = socket.outputStream
                inputStream = socket.inputStream
                connectedAddress = device.address

                Log.d(TAG, "L2CAP channel successfully connected! Sending handshake...")

                // 3-step initialization handshake
                sendRaw(PACKET_HANDSHAKE)
                delay(100)
                sendRaw(PACKET_SET_FEATURES)
                delay(100)
                sendRaw(PACKET_REQUEST_NOTIFICATIONS)

                startReadingLoop()
            } catch (e: Exception) {
                Log.w(TAG, "L2CAP connection failed (standard fallback will remain active): ${e.message}")
                disconnect()
            }
        }
    }

    /**
     * Dispatches Apple Accessory listening mode packet over L2CAP.
     * Off: 0x01, ANC: 0x02, Transparency: 0x03, Adaptive: 0x04
     */
    fun sendNoiseControl(mode: NoiseMode): Boolean {
        val modeVal: Byte = when (mode) {
            NoiseMode.OFF -> 0x01
            NoiseMode.NOISE_CANCELLATION -> 0x02
            NoiseMode.TRANSPARENCY -> 0x03
            NoiseMode.ADAPTIVE -> 0x04
        }
        return sendControlCommand(ID_LISTENING_MODE, modeVal)
    }

    fun sendConversationAwareness(enabled: Boolean): Boolean {
        return sendControlCommand(ID_CONVERSATION_AWARENESS, if (enabled) 0x01 else 0x02)
    }

    fun sendOneBudAnc(enabled: Boolean): Boolean {
        return sendControlCommand(ID_ONE_BUD_ANC, if (enabled) 0x01 else 0x02)
    }

    fun sendVolumeSwipe(enabled: Boolean): Boolean {
        return sendControlCommand(ID_VOLUME_SWIPE, if (enabled) 0x01 else 0x02)
    }

    fun sendAdaptiveVolume(enabled: Boolean): Boolean {
        return sendControlCommand(ID_ADAPTIVE_VOLUME, if (enabled) 0x01 else 0x02)
    }

    /**
     * Builds standard 11-byte Apple accessory control packet:
     * Header (04 00 04 00) + Opcode (09 00) + Identifier + 4 bytes data
     */
    fun buildControlPacket(identifier: Byte, data1: Byte, data2: Byte = 0, data3: Byte = 0, data4: Byte = 0): ByteArray {
        return HEADER_CONTROL + CMD_CONTROL + byteArrayOf(identifier, data1, data2, data3, data4)
    }

    private fun sendControlCommand(identifier: Byte, value: Byte): Boolean {
        val packet = buildControlPacket(identifier, value)
        return sendRaw(packet)
    }

    @Synchronized
    fun sendRaw(packet: ByteArray): Boolean {
        return try {
            val out = outputStream ?: return false
            out.write(packet)
            out.flush()
            Log.d(TAG, "L2CAP TX [${packet.size}b]: ${packet.joinToString(" ") { "%02X".format(it) }}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "L2CAP TX error: ${e.message}")
            false
        }
    }

    private fun startReadingLoop() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(1024)
            val stream = inputStream ?: return@launch

            while (isActive && isConnected) {
                try {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead > 0) {
                        val packet = buffer.copyOfRange(0, bytesRead)
                        parseIncomingPacket(packet)
                    } else if (bytesRead == -1) {
                        Log.d(TAG, "L2CAP stream closed by remote device")
                        break
                    }
                } catch (e: Exception) {
                    if (isActive) Log.w(TAG, "L2CAP read error: ${e.message}")
                    break
                }
            }
            disconnect()
        }
    }

    /**
     * Parses incoming notifications from Apple hardware.
     */
    fun parseIncomingPacket(packet: ByteArray) {
        if (packet.size < 6) return
        if (!packet.startsWith(HEADER_CONTROL)) return

        when (packet[4]) {
            OPCODE_EAR_DETECTION -> {
                // Header (04 00 04 00 06 00) + left status + right status
                if (packet.size >= 8) {
                    val leftStatus = packet[6]
                    val rightStatus = packet[7]
                    // 0x00 = InEar, 0x01 = NotInEar, 0x02 = InCase
                    val leftInEar = leftStatus == 0x00.toByte()
                    val rightInEar = rightStatus == 0x00.toByte()
                    Log.d(TAG, "Hardware In-Ear status: Left=$leftInEar ($leftStatus), Right=$rightInEar ($rightStatus)")
                    BluetoothTracker.setInEarStatus(leftInEar, rightInEar)
                }
            }

            OPCODE_BATTERY_STATUS -> {
                // Size 22 bytes: 3 components (Left, Right, Case)
                if (packet.size == 22) {
                    parseBattery22(packet)
                }
            }

            OPCODE_CONTROL_COMMAND -> {
                if (packet.size >= 8 && packet[6] == ID_LISTENING_MODE) {
                    val modeVal = packet[7]
                    val mode = when (modeVal) {
                        0x01.toByte() -> NoiseMode.OFF
                        0x02.toByte() -> NoiseMode.NOISE_CANCELLATION
                        0x03.toByte() -> NoiseMode.TRANSPARENCY
                        0x04.toByte() -> NoiseMode.ADAPTIVE
                        else -> null
                    }
                    if (mode != null) {
                        Log.d(TAG, "Hardware stem toggled listening mode to: $mode")
                        me.anypods.app.anc.NoiseControlManager.syncFromHardware(mode)
                    }
                }
            }
        }
    }

    private fun parseBattery22(packet: ByteArray) {
        var left = -1
        var leftChg = false
        var right = -1
        var rightChg = false
        var case = -1
        var caseChg = false

        val count = packet[6].toInt()
        val limit = minOf(count, 3)

        for (i in 0 until limit) {
            val offset = 7 + (5 * i)
            if (offset + 4 >= packet.size) break
            val type = packet[offset].toInt()
            val level = packet[offset + 2].toInt() and 0xFF
            val status = packet[offset + 3].toInt() and 0xFF
            val isCharging = status == 0x01

            when (type) {
                0x04 -> { left = level; leftChg = isCharging } // Left bud
                0x02 -> { right = level; rightChg = isCharging } // Right bud
                0x08 -> { case = level; caseChg = isCharging } // Case
                0x01 -> { left = level; right = level; leftChg = isCharging; rightChg = isCharging } // Max
            }
        }

        if (left >= 0 || right >= 0 || case >= 0) {
            Log.d(TAG, "Hardware Battery: L=$left% (chg=$leftChg), R=$right% (chg=$rightChg), Case=$case% (chg=$caseChg)")
            BluetoothTracker.updateBatteryFromL2cap(left, leftChg, right, rightChg, case, caseChg)
        }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        try { inputStream?.close() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        try { activeSocket?.close() } catch (_: Exception) {}
        activeSocket = null
        inputStream = null
        outputStream = null
        connectedAddress = null
    }

    /**
     * Creates an L2CAP socket using native API (API 29+) or reflection constructor fallback.
     */
    private fun createL2capSocket(device: BluetoothDevice): BluetoothSocket {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return device.createInsecureL2capChannel(PSM_APPLE_CONTROL)
            } catch (e: Exception) {
                Log.d(TAG, "Standard createInsecureL2capChannel failed: ${e.message}, falling back to constructor reflection")
            }
        }

        val uuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")
        val type = 3 // L2CAP
        val adapter = BluetoothAdapter.getDefaultAdapter()

        val specs = listOf(
            arrayOf(adapter, device, type, true, true, PSM_APPLE_CONTROL, uuid),
            arrayOf(device, type, true, true, PSM_APPLE_CONTROL, uuid),
            arrayOf(device, type, 1, true, true, PSM_APPLE_CONTROL, uuid),
            arrayOf(type, 1, true, true, device, PSM_APPLE_CONTROL, uuid),
            arrayOf(type, true, true, device, PSM_APPLE_CONTROL, uuid)
        )

        for (params in specs) {
            try {
                val paramTypes = params.map {
                    it?.let { p -> p::class.javaPrimitiveType ?: p::class.java } ?: Any::class.java
                }.toTypedArray()
                val constructor = BluetoothSocket::class.java.getDeclaredConstructor(*paramTypes)
                constructor.isAccessible = true
                return constructor.newInstance(*params) as BluetoothSocket
            } catch (_: Exception) {}
        }

        // Ultimate fallback: RFCOMM
        return device.createInsecureRfcommSocketToServiceRecord(uuid.uuid)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (this.size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }
}
