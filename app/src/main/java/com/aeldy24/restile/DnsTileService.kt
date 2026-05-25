package com.aeldy24.restile

import android.os.Build
import android.service.quicksettings.Tile

class DnsTileService : BaseTileService() {

  override fun onStartListening() {
    super.onStartListening()
    DnsManager.syncFromSystem(this)
    updateTile()
  }

  override fun onClick() {
    super.onClick()
    if (hasSystemAlertWindow()) {
        val intent = android.content.Intent(android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        sendBroadcast(intent)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            FloatingViewManager.showDns(this)
        }, 200)
    } else {
        launchDialog(DnsDialogActivity::class.java)
    }
  }

  private fun updateTile() {
    val tile    = qsTile ?: return
    val current = DnsManager.getCurrentOption(this)
    tile.state  = Tile.STATE_ACTIVE
    tile.label  = getString(R.string.dns_tile_label)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      tile.subtitle = current.label
    }
    tile.updateTile()
  }
}
