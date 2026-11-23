package me.anypods.app.model

data class EarbudState(
    val name: String = "Wireless Earbuds",
    val address: String = "",
    val isConnected: Boolean = false,
    val leftBattery: Int = 100,
    val rightBattery: Int = 100,
    val caseBattery: Int = 50,
    val isLeftCharging: Boolean = false,
    val isRightCharging: Boolean = false,
    val isCaseCharging: Boolean = false,
    val codec: String = "AAC / SBC HD",
    val bassBoostStrength: Short = 0,
    val eqPreset: String = "Balanced"
) {
    val overallBattery: Int
        get() {
            val list = listOf(leftBattery, rightBattery).filter { it >= 0 }
            return if (list.isNotEmpty()) list.average().toInt() else -1
        }
}
