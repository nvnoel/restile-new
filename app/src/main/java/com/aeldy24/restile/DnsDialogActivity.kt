package com.aeldy24.restile

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class DnsDialogActivity : BaseDialogActivity() {

  private var dnsDialog: Dialog? = null
  private var pendingOpt: DnsOption? = null
  private val rows = mutableListOf<View>()

  private lateinit var layoutList: View
  private lateinit var btnApply: MaterialButton
  private lateinit var btnCancel: MaterialButton
  private lateinit var listScroll: NestedScrollView
  private lateinit var container: LinearLayout

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    when {
      !DnsManager.isSupported -> {
        Toast.makeText(this, R.string.dns_requires_android9, Toast.LENGTH_LONG).show()
        finish()
      }
      !hasWriteSecureSettings() -> showPermissionDialog()
      else -> {
        DnsManager.syncFromSystem(this)
        showDnsDialog()
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    dnsDialog?.setOnDismissListener(null)
    dnsDialog?.dismiss()
    dnsDialog = null
  }

  private fun showDnsDialog() {
    val d = Dialog(this).apply {
      requestWindowFeature(Window.FEATURE_NO_TITLE)
      setContentView(R.layout.dialog_dns)
      window?.applyDialogWindowStyle()
      setCancelable(false)
      setOnDismissListener { finish() }
    }
    dnsDialog = d

    initViews(d)
    setupInitialState()
    setupListeners()

    d.show()
  }

  private fun initViews(d: Dialog) {
    listScroll = d.findViewById(R.id.listScroll)
    container  = d.findViewById(R.id.dnsListContainer)
    layoutList = d.findViewById(R.id.layoutDnsList)
    btnApply   = d.findViewById(R.id.btnDnsApply)
    btnCancel  = d.findViewById(R.id.btnDnsCancel)
  }

  private fun setupInitialState() {
    val currentId  = DnsManager.getCurrentId(this)
    val inflater   = LayoutInflater.from(this)

    // Set initial pending option to the currently active one
    pendingOpt = DnsManager.options.find { it.id == currentId }

    // Ensure the scroll view never takes more than ~35% of the screen height in landscape
    // to prevent it from squishing the buttons below it.
    // Logic pembatasan height dihapus karena XML (weight=1, height=0dp)
    // sudah cukup mengatur layout DNS agar tidak gepeng tanpa konflik tinggi fix

    DnsManager.options.forEach { opt ->
      val row = inflater.inflate(R.layout.item_dns, container, false)
      rows.add(row)

      val isSelected = opt.id == pendingOpt?.id
      updateRowUi(row, isSelected, opt, animate = false)

      row.setOnClickListener {
        if (pendingOpt?.id != opt.id) {
          vibrateClick()
          pendingOpt = opt
          updateAllRows()
        }
      }
      container.addView(row)
    }
  }

  private fun setupListeners() {
    btnApply.setOnClickListener {
      vibrateClick()
      pendingOpt?.let { applyDns(it) }
    }
    btnCancel.setOnClickListener {
      vibrateClick()
      dismissDialog()
      finish()
    }
  }

  private fun updateAllRows() {
    DnsManager.options.forEachIndexed { index, opt ->
      val row = rows[index]
      val isSelected = opt.id == pendingOpt?.id
      updateRowUi(row, isSelected, opt, animate = true)
    }
  }

  private fun updateRowUi(row: View, isSelected: Boolean, opt: DnsOption, animate: Boolean) {
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

  private fun applyDns(opt: DnsOption) {
    dismissDialog()
    val ok  = DnsManager.apply(this, opt)
    val msg = if (ok) getString(R.string.dns_apply_success, opt.label)
              else getString(R.string.dns_apply_failed)
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    finish()
  }

  private fun dismissDialog() {
    dnsDialog?.setOnDismissListener(null)
    dnsDialog?.dismiss()
  }
}
