package com.aeldy24.restile

import android.os.Build
import android.service.quicksettings.Tile

class ResolutionTileService : BaseTileService() {

  override fun onStartListening() {
    super.onStartListening()
    updateTile()
  }

  override fun onClick() {
    super.onClick()
    launchDialog(ResolutionDialogActivity::class.java)
  }

  private fun updateTile() {
    val tile    = qsTile ?: return
    val percent = ResolutionManager.currentPct(this)
    tile.state  = Tile.STATE_ACTIVE
    tile.label  = getString(R.string.tile_label)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      tile.subtitle = "$percent%"
    }
    tile.updateTile()
  }
}
