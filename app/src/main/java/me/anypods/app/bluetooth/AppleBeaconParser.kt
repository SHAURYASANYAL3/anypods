package me.anypods.app.bluetooth

data class AppleBeaconInfo(
    val modelId: String,
    val leftBattery: Int,
    val rightBattery: Int,
    val caseBattery: Int,
    val isLeftCharging: Boolean,
    val isRightCharging: Boolean,
    val isCaseCharging: Boolean,
    val isLeftInEar: Boolean,
    val isRightInEar: Boolean,
    val rawHex: String
)

object AppleBeaconParser {
    const val APPLE_MANUFACTURER_ID = 76 // 0x004C

    fun parse(manufacturerData: ByteArray?): AppleBeaconInfo? {
        if (manufacturerData == null || manufacturerData.size < 16) return null
        val hex = manufacturerData.joinToString("") { String.format("%02X", it) }
        return parseHex(hex)
    }

    fun parseHex(hex: String): AppleBeaconInfo? {
        if (hex.length < 16) return null

        val offset = if (hex.startsWith("0719")) {
            0
        } else if (hex.contains("0719")) {
            hex.indexOf("0719")
        } else {
            -1
        }

        val status = if (offset >= 0 && hex.length >= offset + 16) {
            hex.substring(offset)
        } else {
            hex
        }

        if (status.length < 16) return null

        return try {
            val isFlipped = (status[10].digitToInt(16) and 0x02) == 0

            val leftRaw = status[if (isFlipped) 12 else 13].digitToInt(16)
            val rightRaw = status[if (isFlipped) 13 else 12].digitToInt(16)
            val caseRaw = status[15].digitToInt(16)

            // Convert 0-10 nibbles to 0-100% battery (10 = 100%, 0-9 = value * 10 + 5 or value * 10; 15 = disconnected / out of case)
            val leftBattery = if (leftRaw in 0..10) (if (leftRaw == 10) 100 else leftRaw * 10 + 5).coerceIn(0, 100) else -1
            val rightBattery = if (rightRaw in 0..10) (if (rightRaw == 10) 100 else rightRaw * 10 + 5).coerceIn(0, 100) else -1
            val caseBattery = if (caseRaw in 0..10) (if (caseRaw == 10) 100 else caseRaw * 10 + 5).coerceIn(0, 100) else -1

            val chargeStatus = status[14].digitToInt(16)
            val chargeL = (chargeStatus and (if (isFlipped) 0b0010 else 0b0001)) != 0
            val chargeR = (chargeStatus and (if (isFlipped) 0b0001 else 0b0010)) != 0
            val chargeCase = (chargeStatus and 0b0100) != 0

            val inEarStatus = status[11].digitToInt(16)
            val inEarL = (inEarStatus and (if (isFlipped) 0b1000 else 0b0010)) != 0
            val inEarR = (inEarStatus and (if (isFlipped) 0b0010 else 0b1000)) != 0

            val idFull = if (status.length >= 10) status.substring(6, 10).uppercase() else ""
            val idSingle = if (status.length >= 8) status[7].uppercaseChar() else ' '

            val modelId = when {
                idFull == "0220" -> "airpods_1"
                idFull == "0F20" -> "airpods_2"
                idFull == "1320" -> "airpods_3"
                idFull == "1B20" -> "airpods_4"
                idFull == "1C20" -> "airpods_4_anc"
                idFull == "0E20" -> "airpods_pro_1"
                idFull == "1420" || idFull == "2420" -> "airpods_pro_2"
                idFull == "2720" -> "airpods_pro_2"
                idSingle == 'A' || idFull.startsWith("0A") -> "airpods_max"
                idSingle == 'B' -> "beats_studio_buds"
                idFull == "0520" || idFull == "1020" -> "beats_studio_buds"
                else -> "airpods_pro_2"
            }

            AppleBeaconInfo(
                modelId = modelId,
                leftBattery = leftBattery,
                rightBattery = rightBattery,
                caseBattery = caseBattery,
                isLeftCharging = chargeL,
                isRightCharging = chargeR,
                isCaseCharging = chargeCase,
                isLeftInEar = inEarL,
                isRightInEar = inEarR,
                rawHex = status
            )
        } catch (_: Exception) {
            null
        }
    }

    fun isAppleAirPodsBeacon(manufacturerId: Int, data: ByteArray?): Boolean {
        if (manufacturerId != APPLE_MANUFACTURER_ID || data == null || data.size < 16) return false
        val hex = data.joinToString("") { String.format("%02X", it) }
        return hex.startsWith("0719") || hex.contains("0719")
    }
}
