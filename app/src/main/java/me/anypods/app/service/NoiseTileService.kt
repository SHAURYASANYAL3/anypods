package me.anypods.app.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import me.anypods.app.anc.NoiseControlManager
import me.anypods.app.anc.NoiseMode

class NoiseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val current = NoiseControlManager.currentMode.value
        val next = when (current) {
            NoiseMode.OFF -> NoiseMode.NOISE_CANCELLATION
            NoiseMode.NOISE_CANCELLATION -> NoiseMode.TRANSPARENCY
            NoiseMode.TRANSPARENCY -> NoiseMode.ADAPTIVE
            NoiseMode.ADAPTIVE -> NoiseMode.OFF
        }

        NoiseControlManager.setMode(this, next)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val current = NoiseControlManager.currentMode.value

        tile.label = "Noise Control"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (current) {
                NoiseMode.NOISE_CANCELLATION -> "Noise Cancellation"
                NoiseMode.TRANSPARENCY -> "Transparency"
                NoiseMode.ADAPTIVE -> "Adaptive Audio"
                NoiseMode.OFF -> "Off"
            }
        }

        tile.state = when (current) {
            NoiseMode.OFF -> Tile.STATE_INACTIVE
            else -> Tile.STATE_ACTIVE
        }

        tile.updateTile()
    }
}
