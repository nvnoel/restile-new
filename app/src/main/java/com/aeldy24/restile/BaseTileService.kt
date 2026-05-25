package com.aeldy24.restile

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService

abstract class BaseTileService : TileService() {
  protected fun hasSystemAlertWindow(): Boolean {
    return Settings.canDrawOverlays(this)
  }

  protected fun requestSystemAlertWindow() {
      val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      startActivityAndCollapse(intent)
  }

  // API 34+: startActivityAndCollapse(Intent) deprecated → wajib PendingIntent
  protected fun launchDialog(cls: Class<*>) {
    val intent = Intent(this, cls).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
        startActivityAndCollapse(
          PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        )
      } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
      }
    } catch (e: Throwable) {
      startActivity(intent) // Fallback normal start activity as an extreme safe-case if collapse causes crash
    }
  }
}
