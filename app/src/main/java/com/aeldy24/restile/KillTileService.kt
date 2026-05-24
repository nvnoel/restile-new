package com.aeldy24.restile

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class KillTileService : TileService() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = getString(R.string.kill_tile_label)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        killBackgroundApps()

        // Tutup QS panel
        val intent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        sendBroadcast(intent)
    }

    private fun killBackgroundApps() {
        executor.execute {
            var isShizukuOk = false
            try {
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    isShizukuOk = true
                }
            } catch (e: Throwable) {
                isShizukuOk = false
            }

            if (isShizukuOk) {
                killViaShizuku()
            } else {
                killViaAm()
            }
        }
    }

    private fun killViaAm() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val myPackage = packageName

            var killedCount = 0
            for (appInfo in packages) {
                // Lewati system apps dan diri sendiri
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                if (appInfo.packageName == myPackage) continue

                am.killBackgroundProcesses(appInfo.packageName)
                killedCount++
            }

            showToastSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            showToastError(e.message ?: "Failed to kill apps")
        }
    }

    private fun killViaShizuku() {
        try {
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val myPackage = packageName

            val packageList = mutableListOf<String>()
            for (appInfo in packages) {
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                if (appInfo.packageName == myPackage) continue
                packageList.add(appInfo.packageName)
            }

            val shizukuNewProcess = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            ).also { it.isAccessible = true }

            for (pkg in packageList) {
                try {
                    val proc = shizukuNewProcess.invoke(null, arrayOf("am", "force-stop", pkg), null, null) as Process
                    proc.waitFor()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            showToastSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            showToastError(e.message ?: "Failed to kill apps via Shizuku")
        }
    }

    private fun showToastSuccess() {
        mainHandler.post {
            Toast.makeText(this, R.string.kill_success_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showToastError(msg: String) {
        mainHandler.post {
            Toast.makeText(this, "Error: $msg", Toast.LENGTH_SHORT).show()
        }
    }
}
