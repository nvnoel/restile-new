package com.aeldy24.restile
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import java.util.concurrent.Executors
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
@SuppressLint("PrivateApi")
object ResolutionManager {
  private const val PREFS       = "res_prefs"
  private const val KEY_PCT     = "percent"
  private const val KEY_BASE_W  = "base_w"
  private const val KEY_BASE_H  = "base_h"
  private const val KEY_BASE_DPI= "base_dpi"
  private const val KEY_PHYS_W  = "phys_w"
  private const val KEY_PHYS_H  = "phys_h"
  private const val KEY_PHYS_DPI= "phys_dpi"
  private const val KEY_LAST_W  = "last_w"
  private const val KEY_LAST_H  = "last_h"
  private const val USER_SELF   = -3   // UserHandle.USER_CURRENT_OR_SELF
  private const val DEFAULT_W   = 1080
  private const val DEFAULT_H   = 1920
  private const val DEFAULT_DPI = 420
  private const val SYNC_TOL    = 4
  private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "ResMgr-bg") }
  private val main     = Handler(Looper.getMainLooper())

  private inline fun SharedPreferences.edit(operation: (SharedPreferences.Editor) -> Unit) {
    val editor = edit()
    operation(editor)
    editor.apply()
  }

  init {
    // Bypass hidden API blacklist
    try {
      HiddenApiBypass.addHiddenApiExemptions("L")
    } catch (e: Throwable) {
      Log.w("ResMgr", "Hidden-API bypass: ${e.message}")
    }
  }
  private val wms: Any by lazy(LazyThreadSafetyMode.PUBLICATION) {
    var service: Any? = null
    try {
      if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        val windowBinder = SystemServiceHelper.getSystemService("window")
        val shizukuBinder = rikka.shizuku.ShizukuBinderWrapper(windowBinder)
        service = Class.forName("android.view.IWindowManager\$Stub").getDeclaredMethod("asInterface", android.os.IBinder::class.java).invoke(null, shizukuBinder)
      }
    } catch (e: Throwable) {
      Log.w("ResMgr", "Gagal menghubungkan Shizuku ke wms: ${e.message}")
    }

    service ?: runCatching {
      Class.forName("android.view.WindowManagerGlobal")
        .getMethod("getWindowManagerService").invoke(null)
    }.getOrNull()!!
  }
  private val iwm: Class<*> by lazy(LazyThreadSafetyMode.PUBLICATION) { Class.forName("android.view.IWindowManager") }
  private val mSetSize    by lazy(LazyThreadSafetyMode.NONE) {
    iwm.getMethod("setForcedDisplaySize", Int::class.java, Int::class.java, Int::class.java)
  }
  private val mClearSize  by lazy(LazyThreadSafetyMode.NONE) { iwm.getMethod("clearForcedDisplaySize", Int::class.java) }
  private val mSetDpiUser by lazy(LazyThreadSafetyMode.NONE) {
    runCatching {
      iwm.getMethod("setForcedDisplayDensityForUser", Int::class.java, Int::class.java, Int::class.java)
    }.getOrNull()
  }
  private val mSetDpiLegacy by lazy(LazyThreadSafetyMode.NONE) {
    runCatching { iwm.getMethod("setForcedDisplayDensity", Int::class.java, Int::class.java) }.getOrNull()
  }
  private val mClearDpiUser by lazy(LazyThreadSafetyMode.NONE) {
    runCatching {
      iwm.getMethod("clearForcedDisplayDensityForUser", Int::class.java, Int::class.java)
    }.getOrNull()
  }
  private val mClearDpiLegacy by lazy(LazyThreadSafetyMode.NONE) {
    runCatching { iwm.getMethod("clearForcedDisplayDensity", Int::class.java) }.getOrNull()
  }
  fun interface Callback { fun onResult(ok: Boolean) }
  fun saveBaseResolution(ctx: Context) {
    val p = prefs(ctx)
    if (p.getInt(KEY_BASE_W, 0) != 0) return
    var physW = 0; var physH = 0; var physDpi = 0
    var ovrW  = 0; var ovrH  = 0; var ovrDpi  = 0
    readWm("size") { line ->
      when {
        line.startsWith("Physical size:")  -> parseWH(line)?.let { (w, h) -> physW = w; physH = h }
        line.startsWith("Override size:")  -> parseWH(line)?.let { (w, h) -> ovrW  = w; ovrH  = h }
      }
    }
    readWm("density") { line ->
      when {
        line.startsWith("Physical density:") -> parseInt(line)?.let { physDpi = it }
        line.startsWith("Override density:") -> parseInt(line)?.let { ovrDpi  = it }
      }
    }
    // Abort if physical info reading fails
    if (physW == 0 || physH == 0 || physDpi == 0) return

    val isOvr = ovrW > 0
    p.edit {
      it.putInt(KEY_BASE_W,    if (isOvr) ovrW else physW)
        .putInt(KEY_BASE_H,    if (isOvr) ovrH else physH)
        .putInt(KEY_BASE_DPI,  if (ovrDpi > 0) ovrDpi else physDpi)
        .putInt(KEY_PHYS_W,    physW)
        .putInt(KEY_PHYS_H,    physH)
        .putInt(KEY_PHYS_DPI,  physDpi)
        .putInt(KEY_LAST_W,    if (isOvr) ovrW else physW)
        .putInt(KEY_LAST_H,    if (isOvr) ovrH else physH)
    }
  }
  fun checkSync(ctx: Context, @MainThread cb: (Boolean) -> Unit) {
    executor.execute {
      val p     = prefs(ctx)
      val lastW = p.getInt(KEY_LAST_W, 0)
      val lastH = p.getInt(KEY_LAST_H, 0)
      if (lastW == 0) { main.post { cb(true) }; return@execute }
      val dm = DisplayMetrics()
      try {
        val dispMgr = ctx.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dispMgr.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(dm)
      } catch (e: Throwable) {
        main.post { cb(true) }; return@execute
      }
      // Sort to ignore orientation
      val curMin = minOf(dm.widthPixels, dm.heightPixels)
      val curMax = maxOf(dm.widthPixels, dm.heightPixels)
      val expMin = minOf(lastW, lastH)
      val expMax = maxOf(lastW, lastH)
      val inSync = kotlin.math.abs(curMin - expMin) <= SYNC_TOL &&
                   kotlin.math.abs(curMax - expMax) <= SYNC_TOL
      main.post { cb(inSync) }
    }
  }
  fun calcRes(ctx: Context, pct: Int): IntArray {
    val p = prefs(ctx)
    return intArrayOf(
      (p.getInt(KEY_BASE_W, DEFAULT_W) * pct / 100.0).toInt(),
      (p.getInt(KEY_BASE_H, DEFAULT_H) * pct / 100.0).toInt()
    )
  }
  fun calcDpi(ctx: Context, pct: Int): Int =
    (prefs(ctx).getInt(KEY_BASE_DPI, DEFAULT_DPI) * pct / 100.0).toInt()
  fun applyResolution(ctx: Context, pct: Int, @MainThread cb: Callback) {
    val res = calcRes(ctx, pct)
    val dpi = calcDpi(ctx, pct)
    executor.execute {
      val ok = runCatching { setSize(res[0], res[1]) }.isSuccess
      if (ok) {
        runCatching { setDpi(dpi) }
        prefs(ctx).edit {
          it.putInt(KEY_PCT,    pct)
            .putInt(KEY_LAST_W, res[0])
            .putInt(KEY_LAST_H, res[1])
        }
      }
      main.post { cb.onResult(ok) }
    }
  }
  fun resetResolution(ctx: Context, @MainThread cb: Callback) {
    val p     = prefs(ctx)
    val physW = p.getInt(KEY_PHYS_W, DEFAULT_W)
    val physH = p.getInt(KEY_PHYS_H, DEFAULT_H)
    executor.execute {
      val ok = runCatching { clearSize() }.isSuccess
      if (ok) {
        runCatching { clearDpi() }
        prefs(ctx).edit {
          it.putInt(KEY_PCT,    100)
            .putInt(KEY_LAST_W, physW)
            .putInt(KEY_LAST_H, physH)
        }
      }
      main.post { cb.onResult(ok) }
    }
  }
  fun currentPct(ctx: Context): Int = prefs(ctx).getInt(KEY_PCT, 100)
  fun physRes(ctx: Context): IntArray = prefs(ctx).let {
    intArrayOf(it.getInt(KEY_PHYS_W, DEFAULT_W), it.getInt(KEY_PHYS_H, DEFAULT_H))
  }
  fun physDpi(ctx: Context): Int = prefs(ctx).getInt(KEY_PHYS_DPI, DEFAULT_DPI)
  fun baseW(ctx: Context): Int = prefs(ctx).getInt(KEY_BASE_W, DEFAULT_W)
  fun baseH(ctx: Context): Int = prefs(ctx).getInt(KEY_BASE_H, DEFAULT_H)
  fun baseDpi(ctx: Context): Int = prefs(ctx).getInt(KEY_BASE_DPI, DEFAULT_DPI)
  @WorkerThread private fun setSize(w: Int, h: Int) = mSetSize.invoke(wms, Display.DEFAULT_DISPLAY, w, h)
  @WorkerThread private fun clearSize() = mClearSize.invoke(wms, Display.DEFAULT_DISPLAY)
  @WorkerThread
  private fun setDpi(dpi: Int) {
    runCatching { mSetDpiLegacy?.invoke(wms, Display.DEFAULT_DISPLAY, dpi) }
    mSetDpiUser?.invoke(wms, Display.DEFAULT_DISPLAY, dpi, USER_SELF)
  }
  @WorkerThread
  private fun clearDpi() {
    runCatching { mClearDpiLegacy?.invoke(wms, Display.DEFAULT_DISPLAY) }
    mClearDpiUser?.invoke(wms, Display.DEFAULT_DISPLAY, USER_SELF)
  }
  // crossinline not allowed with try/finally
  private fun readWm(sub: String, onLine: (String) -> Unit) {
    var proc: Process? = null
    try {
      if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        val shizukuNewProcess = Shizuku::class.java.getDeclaredMethod(
          "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).also { it.isAccessible = true } // Bypass private modifier

        proc = shizukuNewProcess.invoke(null, arrayOf("wm", sub), null, null) as Process
      } else {
        return // Abort without Shizuku permission
      }
      proc.inputStream.bufferedReader().use { it.forEachLine(onLine) }
      proc.waitFor() // Wait for wm output
    } catch (e: Throwable) {
      Log.w("ResMgr", "Gagal membaca wm $sub: ${e.message}")
    } finally {
      proc?.destroy()
    }
  }
  private fun parseWH(line: String): Pair<Int, Int>? = runCatching {
    val p = line.substringAfter(": ").split("x")
    p[0].trim().toInt() to p[1].trim().toInt()
  }.getOrNull()
  private fun parseInt(line: String): Int? = line.substringAfter(": ").trim().toIntOrNull()
  private fun prefs(ctx: Context): SharedPreferences =
    ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
