package com.aeldy24.restile

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.color.DynamicColors

object FloatingViewManager {

    fun showUpscaler(context: Context) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_resolution, null)

        // Memaksa tema untuk mewarisi warna yang tepat, karena Context mungkin application context
        // Kita tidak bisa langsung pakai theme attributes. Sebagai workaround kita bisa bungkus contextnya.
        // Tapi R.layout.dialog_resolution pakai ?attr, jadi lebih baik wrap dengan ContextThemeWrapper.
        val themedContext = android.view.ContextThemeWrapper(context, R.style.Theme_ResTile)
        val themedInflater = LayoutInflater.from(themedContext)
        val themedView = themedInflater.inflate(R.layout.dialog_resolution, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.dimAmount = 0.6f

        setupUpscalerLogic(themedContext, themedView, wm)

        try {
            wm.addView(themedView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupUpscalerLogic(context: Context, view: View, wm: WindowManager) {
        // ... Logika serupa dengan ResolutionDialogActivity
        var curPct = ResolutionManager.currentPct(context)
        var pendingPct = curPct
        var baseW = ResolutionManager.baseW(context)
        var baseH = ResolutionManager.baseH(context)
        var baseDpi = ResolutionManager.baseDpi(context)
        var outOfSync = false
        var confirmState = 0 // 0: None, 1: Apply, 2: Reset

        val slider = view.findViewById<Slider>(R.id.seekBar)
        val tvPct = view.findViewById<TextView>(R.id.tvPercent)
        val tvRes = view.findViewById<TextView>(R.id.tvResolution)
        val tvDpi = view.findViewById<TextView>(R.id.tvDensity)
        val tvSub = view.findViewById<TextView>(R.id.tvPreviewLabel)

        val layoutSlider = view.findViewById<LinearLayout>(R.id.layoutSlider)
        val tvConfirmMessage = view.findViewById<TextView>(R.id.tvConfirmMessage)

        val layoutButtonsNormal = view.findViewById<LinearLayout>(R.id.layoutButtonsNormal)
        val layoutButtonsConfirm = view.findViewById<LinearLayout>(R.id.layoutButtonsConfirm)

        val btnApplyNormal = view.findViewById<MaterialButton>(R.id.btnApplyNormal)
        val btnResetNormal = view.findViewById<MaterialButton>(R.id.btnResetNormal)
        val btnCancelNormal = view.findViewById<MaterialButton>(R.id.btnCancelNormal)

        val btnConfirmAction = view.findViewById<MaterialButton>(R.id.btnConfirmAction)
        val btnCancelConfirm = view.findViewById<MaterialButton>(R.id.btnCancelConfirm)

        fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()

        fun lerpColor(pct: Int): Int {
            val t = ((pct - 100) / 125f).coerceIn(0f, 1f)
            val r: Int; val g: Int; val b: Int
            if (t <= 0.5f) {
                val u = t / 0.5f
                r = lerp(0x4C, 0xFF, u); g = lerp(0xAF, 0x98, u); b = lerp(0x50, 0x00, u)
            } else {
                val u = (t - 0.5f) / 0.5f
                r = lerp(0xFF, 0xF4, u); g = lerp(0x98, 0x43, u); b = lerp(0x00, 0x36, u)
            }
            return Color.rgb(r, g, b)
        }

        fun updateDisplay(pct: Int, isPending: Boolean) {
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
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                color = typedValue.data
            }
            tvPct.setTextColor(color)
            tvPct.paint.clearShadowLayer()
            tvPct.invalidate()
        }

        slider.value = curPct.toFloat()
        updateDisplay(curPct, false)

        ResolutionManager.checkSync(context) { inSync ->
            if (!inSync) {
                outOfSync = true
                tvPct.text = "?"
                tvPct.setTextColor(Color.parseColor("#FF4444"))
                tvPct.paint.clearShadowLayer()
                tvPct.invalidate()
                tvSub.text = context.getString(R.string.preview_out_of_sync)
                tvRes.text = "— × — px"
                tvDpi.text = "— dpi"
            }
        }

        slider.addOnChangeListener { _, value, fromUser ->
            pendingPct = value.toInt()
            if (outOfSync && fromUser) {
                outOfSync = false
                tvSub.text = context.getString(R.string.preview_ready)
            }
            updateDisplay(pendingPct, pendingPct != curPct)
            if (fromUser) {
                 VibrationHelper.tick(context)
            }
        }

        fun beginTransition() {
            (view as? android.view.ViewGroup)?.let { parentView ->
              val transition = androidx.transition.AutoTransition().apply {
                interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
                duration = 300
              }
              TransitionManager.beginDelayedTransition(parentView, transition)
            }
        }

        btnApplyNormal.setOnClickListener {
            VibrationHelper.click(context)
            confirmState = 1
            beginTransition()
            layoutSlider.visibility = View.GONE
            tvConfirmMessage.text = context.getString(R.string.confirm_apply_label)
            tvConfirmMessage.visibility = View.VISIBLE
            layoutButtonsNormal.visibility = View.GONE
            layoutButtonsConfirm.visibility = View.VISIBLE
            tvSub.text = "do with your own risk"
        }

        btnResetNormal.setOnClickListener {
            VibrationHelper.click(context)
            confirmState = 2

            val pr = ResolutionManager.physRes(context)
            val pd = ResolutionManager.physDpi(context)
            tvPct.text = "Original"
            tvRes.text = "${pr[0]} × ${pr[1]} px"
            tvDpi.text = "$pd dpi"

            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            tvPct.setTextColor(typedValue.data)
            tvPct.paint.clearShadowLayer()
            tvPct.invalidate()

            beginTransition()
            layoutSlider.visibility = View.GONE
            tvConfirmMessage.text = context.getString(R.string.confirm_reset_label)
            tvConfirmMessage.visibility = View.VISIBLE
            layoutButtonsNormal.visibility = View.GONE
            layoutButtonsConfirm.visibility = View.VISIBLE
            tvSub.text = "do with your own risk"
        }

        btnCancelNormal.setOnClickListener {
            VibrationHelper.click(context)
            wm.removeView(view)
        }

        btnCancelConfirm.setOnClickListener {
            VibrationHelper.click(context)
            confirmState = 0
            beginTransition()
            layoutSlider.visibility = View.VISIBLE
            tvConfirmMessage.visibility = View.GONE
            layoutButtonsNormal.visibility = View.VISIBLE
            layoutButtonsConfirm.visibility = View.GONE
            tvSub.text = context.getString(R.string.preview_ready)
            updateDisplay(pendingPct, pendingPct != curPct)
        }

        btnConfirmAction.setOnClickListener {
            VibrationHelper.click(context)
            btnConfirmAction.isEnabled = false
            btnCancelConfirm.isEnabled = false
            wm.removeView(view)

            Handler(Looper.getMainLooper()).postDelayed({
                if (confirmState == 1) {
                    ResolutionManager.applyResolution(context, pendingPct) { ok ->
                        Toast.makeText(context, if (ok) R.string.apply_success_toast else R.string.apply_failed, Toast.LENGTH_SHORT).show()
                    }
                } else if (confirmState == 2) {
                    ResolutionManager.resetResolution(context) { ok ->
                        Toast.makeText(context, if (ok) R.string.reset_success else R.string.reset_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }, 300)
        }
    }

    // ----------- DNS -----------

    fun showDns(context: Context) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val themedContext = android.view.ContextThemeWrapper(context, R.style.Theme_ResTile)
        val themedInflater = LayoutInflater.from(themedContext)
        val themedView = themedInflater.inflate(R.layout.dialog_dns, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.dimAmount = 0.6f

        setupDnsLogic(themedContext, themedView, wm)

        try {
            wm.addView(themedView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupDnsLogic(context: Context, view: View, wm: WindowManager) {
        val container = view.findViewById<LinearLayout>(R.id.dnsListContainer)
        val btnApply = view.findViewById<MaterialButton>(R.id.btnDnsApply)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnDnsCancel)

        val currentId = DnsManager.getCurrentId(context)
        var pendingOpt = DnsManager.options.find { it.id == currentId }
        val rows = mutableListOf<View>()
        val inflater = LayoutInflater.from(context)

        fun updateRowUi(row: View, isSelected: Boolean, opt: DnsOption, animate: Boolean) {
            row.findViewById<ImageView>(R.id.ivDnsIcon).setImageResource(opt.iconRes)
            val tvName = row.findViewById<TextView>(R.id.tvDnsName)
            tvName.text = opt.label

            val bgSelected = row.findViewById<View>(R.id.bgSelected)
            val ivCheck = row.findViewById<ImageView>(R.id.ivDnsCheck)

            if (animate) {
              bgSelected.animate().alpha(if (isSelected) 1f else 0f).setDuration(250).start()
              if (isSelected) {
                ivCheck.visibility = View.VISIBLE
                ivCheck.alpha = 0f
                ivCheck.scaleX = 0.5f
                ivCheck.scaleY = 0.5f
                ivCheck.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(350).setInterpolator(android.view.animation.OvershootInterpolator(1.5f)).start()

                row.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).withEndAction {
                  row.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                }.start()
              } else {
                ivCheck.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(200).withEndAction {
                  ivCheck.visibility = View.INVISIBLE
                }.start()
              }
            } else {
              bgSelected.alpha = if (isSelected) 1f else 0f
              ivCheck.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
              ivCheck.alpha = if (isSelected) 1f else 0f
              ivCheck.scaleX = if (isSelected) 1f else 0.8f
              ivCheck.scaleY = if (isSelected) 1f else 0.8f
            }
        }

        fun updateAllRows() {
            DnsManager.options.forEachIndexed { index, opt ->
                updateRowUi(rows[index], opt.id == pendingOpt?.id, opt, true)
            }
        }

        DnsManager.options.forEach { opt ->
            val row = inflater.inflate(R.layout.item_dns, container, false)
            rows.add(row)

            val isSelected = opt.id == pendingOpt?.id
            updateRowUi(row, isSelected, opt, false)

            row.setOnClickListener {
                if (pendingOpt?.id != opt.id) {
                    VibrationHelper.click(context)
                    pendingOpt = opt
                    updateAllRows()
                }
            }
            container.addView(row)
        }

        btnApply.setOnClickListener {
            VibrationHelper.click(context)
            wm.removeView(view)
            pendingOpt?.let { opt ->
                val ok = DnsManager.apply(context, opt)
                val msg = if (ok) context.getString(R.string.dns_apply_success, opt.label)
                          else context.getString(R.string.dns_apply_failed)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            VibrationHelper.click(context)
            wm.removeView(view)
        }
    }
}
