package com.aeldy24.restile

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.card.MaterialCardView
import androidx.constraintlayout.widget.ConstraintLayout

class ResolutionDialogActivity : BaseDialogActivity() {

  private enum class Confirm { APPLY, RESET }

  private lateinit var slider: Slider
  private lateinit var tvPct: TextView
  private lateinit var tvRes: TextView
  private lateinit var tvDpi: TextView
  private lateinit var tvSub: TextView
  private lateinit var layoutSlider: LinearLayout
  private lateinit var tvConfirmMessage: TextView

  private lateinit var layoutButtonsNormal: LinearLayout
  private lateinit var layoutButtonsConfirm: LinearLayout
  private lateinit var btnApplyNormal: MaterialButton
  private lateinit var btnResetNormal: MaterialButton
  private lateinit var btnCancelNormal: MaterialButton

  private lateinit var btnConfirmAction: MaterialButton
  private lateinit var btnCancelConfirm: MaterialButton

  private var dialog: Dialog? = null
  private var curPct      = 100
  private var pendingPct  = 100
  private var lastVibStep = Int.MIN_VALUE
  private var confirmState: Confirm? = null
  private var outOfSync   = false

  private var baseW = 1080
  private var baseH = 1920
  private var baseDpi = 420

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ResolutionManager.saveBaseResolution(this)
    curPct     = ResolutionManager.currentPct(this)
    baseW      = ResolutionManager.baseW(this)
    baseH      = ResolutionManager.baseH(this)
    baseDpi    = ResolutionManager.baseDpi(this)
    pendingPct = curPct

    if (!hasWriteSecureSettings()) showPermissionDialog()
    else showDialog()
  }

  override fun onDestroy() {
    super.onDestroy()
    dialog?.setOnDismissListener(null)
    dialog?.dismiss()
    dialog = null
  }

  private fun showDialog() {
    val d = Dialog(this).apply {
      requestWindowFeature(Window.FEATURE_NO_TITLE)
      setContentView(R.layout.dialog_resolution)
      window?.applyDialogWindowStyle()
      setCancelable(false)
      setOnDismissListener { finish() }
    }
    dialog = d

    initViews(d)
    setupInitialState()
    setupListeners()

    d.show()

  }

  private fun initViews(d: Dialog) {
    slider               = d.findViewById(R.id.seekBar)
    tvPct                = d.findViewById(R.id.tvPercent)
    tvRes                = d.findViewById(R.id.tvResolution)
    tvDpi                = d.findViewById(R.id.tvDensity)
    tvSub                = d.findViewById(R.id.tvPreviewLabel)

    layoutSlider         = d.findViewById(R.id.layoutSlider)
    tvConfirmMessage     = d.findViewById(R.id.tvConfirmMessage)

    layoutButtonsNormal  = d.findViewById(R.id.layoutButtonsNormal)
    layoutButtonsConfirm = d.findViewById(R.id.layoutButtonsConfirm)

    btnApplyNormal       = d.findViewById(R.id.btnApplyNormal)
    btnResetNormal       = d.findViewById(R.id.btnResetNormal)
    btnCancelNormal      = d.findViewById(R.id.btnCancelNormal)

    btnConfirmAction     = d.findViewById(R.id.btnConfirmAction)
    btnCancelConfirm     = d.findViewById(R.id.btnCancelConfirm)
  }

  private fun setupInitialState() {
    slider.value = curPct.toFloat()
    updateDisplay(curPct, isPending = false)
    lastVibStep = curPct

    ResolutionManager.checkSync(this) { inSync ->
      if (!inSync) { outOfSync = true; showOutOfSync() }
    }
  }

  private fun setupListeners() {
    slider.addOnChangeListener { _, value, fromUser ->
      pendingPct = value.toInt()
      if (outOfSync && fromUser) {
        outOfSync = false
        tvSub.text = getString(R.string.preview_ready)
      }
      updateDisplay(pendingPct, isPending = pendingPct != curPct)
      if (fromUser && pendingPct != lastVibStep) {
        lastVibStep = pendingPct
        vibrateTick()
      }
    }

    // Normal state buttons
    btnApplyNormal.setOnClickListener {
      vibrateClick()
      showConfirmApply(pendingPct)
    }
    btnResetNormal.setOnClickListener {
      vibrateClick()
      showConfirmReset()
    }
    btnCancelNormal.setOnClickListener {
      vibrateClick()
      dismissDialog()
      finish()
    }

    // Confirm state buttons
    btnConfirmAction.setOnClickListener {
      vibrateClick()
      when (confirmState) {
        Confirm.APPLY -> doApply(pendingPct)
        Confirm.RESET -> doReset()
        else -> backToMain()
      }
    }
    btnCancelConfirm.setOnClickListener {
      vibrateClick()
      backToMain()
    }
  }

  private fun showOutOfSync() {
    tvPct.text = "?"
    tvPct.setTextColor(COLOR_OUT_OF_SYNC)
    tvPct.paint.clearShadowLayer()
    tvPct.invalidate()
    tvSub.text = getString(R.string.preview_out_of_sync)
    tvRes.text = "— × — px"
    tvDpi.text = "— dpi"
  }

  private fun showConfirmApply(pct: Int) {
    val resW = (baseW * pct / 100.0).toInt()
    val resH = (baseH * pct / 100.0).toInt()
    val dpi = (baseDpi * pct / 100.0).toInt()

    tvConfirmMessage.text = getString(R.string.confirm_apply_label)
    switchToConfirm(Confirm.APPLY)
  }

  private fun showConfirmReset() {
    tvConfirmMessage.text = getString(R.string.confirm_reset_label)

    // Reset UI to original values
    val pr = ResolutionManager.physRes(this)
    val pd = ResolutionManager.physDpi(this)
    tvPct.text = "Original"
    tvRes.text = "${pr[0]} × ${pr[1]} px"
    tvDpi.text = "$pd dpi"

    val typedValue = android.util.TypedValue()
    theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
    tvPct.setTextColor(typedValue.data)
    tvPct.paint.clearShadowLayer()
    tvPct.invalidate()

    switchToConfirm(Confirm.RESET)
  }

  private fun beginTransition() {
    (dialog?.window?.decorView as? android.view.ViewGroup)?.let { parentView ->
      val transition = androidx.transition.AutoTransition().apply {
        interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
        duration = 300
      }
      TransitionManager.beginDelayedTransition(parentView, transition)
    }
  }

  private fun switchToConfirm(state: Confirm) {
    confirmState = state
    beginTransition()

    layoutSlider.visibility = View.GONE
    tvConfirmMessage.visibility = View.VISIBLE

    layoutButtonsNormal.visibility = View.GONE
    layoutButtonsConfirm.visibility = View.VISIBLE
    tvSub.text = "do with your own risk"
  }

  private fun backToMain() {
    confirmState = null
    beginTransition()

    layoutSlider.visibility = View.VISIBLE
    tvConfirmMessage.visibility = View.GONE

    layoutButtonsNormal.visibility = View.VISIBLE
    layoutButtonsConfirm.visibility = View.GONE
    tvSub.text = getString(R.string.preview_ready)

    updateDisplay(pendingPct, isPending = pendingPct != curPct)
  }

  private fun doApply(pct: Int) {
    dismissDialog()
    ResolutionManager.applyResolution(this, pct) { ok ->
      Toast.makeText(this, if (ok) R.string.apply_success_toast else R.string.apply_failed, Toast.LENGTH_SHORT).show()
      finish()
    }
  }

  private fun doReset() {
    dismissDialog()
    ResolutionManager.resetResolution(this) { ok ->
      Toast.makeText(this, if (ok) R.string.reset_success else R.string.reset_failed, Toast.LENGTH_SHORT).show()
      finish()
    }
  }

  private fun dismissDialog() {
    dialog?.setOnDismissListener(null)
    dialog?.dismiss()
  }

  private fun updateDisplay(pct: Int, isPending: Boolean) {
    val resW = (baseW * pct / 100.0).toInt()
    val resH = (baseH * pct / 100.0).toInt()
    val dpi = (baseDpi * pct / 100.0).toInt()
    tvPct.text = "$pct%"
    tvRes.text = "$resW × $resH px"
    tvDpi.text = "$dpi dpi"

    val color: Int
    if (isPending) {
      color = lerpColor(pct)
    } else {
      // Get theme color
      val typedValue = android.util.TypedValue()
      theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
      color = typedValue.data
    }
    tvPct.setTextColor(color)
    tvPct.paint.clearShadowLayer()
    tvPct.invalidate()
  }

  private fun lerpColor(pct: Int): Int {
    // Karena max pct sekarang 225, range adalah 100 sampai 225.
    // Lebar range = 225 - 100 = 125.
    val t = ((pct - 100) / 125f).coerceIn(0f, 1f)
    val r: Int; val g: Int; val b: Int
    // Lerp: green to orange to red
    if (t <= 0.5f) {
      val u = t / 0.5f
      r = lerp(0x4C, 0xFF, u); g = lerp(0xAF, 0x98, u); b = lerp(0x50, 0x00, u)
    } else {
      val u = (t - 0.5f) / 0.5f
      r = lerp(0xFF, 0xF4, u); g = lerp(0x98, 0x43, u); b = lerp(0x00, 0x36, u)
    }
    return Color.rgb(r, g, b)
  }

  private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()

  companion object {
    private val COLOR_OUT_OF_SYNC = Color.parseColor("#FF4444")
  }
}
