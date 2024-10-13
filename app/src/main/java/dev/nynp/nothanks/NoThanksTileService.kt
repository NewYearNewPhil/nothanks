package dev.nynp.nothanks

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class NoThanksTileService : TileService() {

    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        SettingsManager.init(applicationContext)

        job = CoroutineScope(Dispatchers.Main).launch {
            SettingsManager.isBlockingEnabled
                .onEach { isEnabled ->
                    updateTile(isEnabled)
                }
                .launchIn(this)
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        job?.cancel()
    }

    override fun onClick() {
        super.onClick()
        CoroutineScope(Dispatchers.Main).launch {
            val currentState = SettingsManager.isBlockingEnabled.value
            SettingsManager.setBlockingEnabled(!currentState)
        }
    }

    private fun updateTile(isEnabled: Boolean) {
        qsTile?.apply {
            state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (isEnabled) "Blocking On" else "Blocking Off"
            updateTile()
        }
    }
}