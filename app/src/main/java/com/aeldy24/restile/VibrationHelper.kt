package com.aeldy24.restile

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibrationHelper {

  @Volatile private var cachedVibrator: Vibrator? = null
  @Volatile private var isVibratorCached = false

  private val effectClick by lazy(LazyThreadSafetyMode.NONE) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      VibrationEffect.createOneShot(35, 80)
    } else null
  }

  private val effectTick by lazy(LazyThreadSafetyMode.NONE) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      VibrationEffect.createOneShot(12, 30)
    } else null
  }

  fun click(context: Context) = vibrate(context) { vib ->
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      effectClick?.let { vib.vibrate(it) }
    } else {
      @Suppress("DEPRECATION") vib.vibrate(40)
    }
  }

  fun tick(context: Context) = vibrate(context) { vib ->
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      effectTick?.let { vib.vibrate(it) }
    } else {
      @Suppress("DEPRECATION") vib.vibrate(20)
    }
  }

  private fun vibrate(context: Context, block: (Vibrator) -> Unit) {
    val vib = getVibrator(context) ?: return
    try { block(vib) } catch (_: Exception) { }
  }

  private fun getVibrator(context: Context): Vibrator? {
    if (isVibratorCached) return cachedVibrator
    synchronized(this) {
      if (isVibratorCached) return cachedVibrator
      val appContext = context.applicationContext
      cachedVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
          ?.defaultVibrator?.takeIf { it.hasVibrator() }
      } else {
        @Suppress("DEPRECATION")
        (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
          ?.takeIf { it.hasVibrator() }
      }
      isVibratorCached = true
      return cachedVibrator
    }
  }
}
