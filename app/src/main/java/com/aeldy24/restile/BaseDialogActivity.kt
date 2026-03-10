package com.aeldy24.restile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

abstract class BaseDialogActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

  private val SHIZUKU_CODE = 1234

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setupEdgeToEdge()
  }

  private fun setupEdgeToEdge() {
    val win = window
    // Center dialog in safe area
    WindowCompat.setDecorFitsSystemWindows(win, false)
    WindowCompat.getInsetsController(win, win.decorView).apply {
      isAppearanceLightStatusBars = true
      isAppearanceLightNavigationBars = true
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }

  protected fun hasWriteSecureSettings(): Boolean {
    return checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
  }

  protected fun showPermissionDialog() {
    var hasPermission = false
    var isPingOk = false
    try {
      isPingOk = Shizuku.pingBinder()
      if (isPingOk) {
        hasPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
      }
    } catch (e: Throwable) {
      isPingOk = false
    }

    if (!isPingOk || !hasPermission) {
      showShizukuCustomDialog(isPingOk)
      return
    }

    try {
      grantPermissionViaShizuku()
      // Jika dipanggil kembali ke sini namun sudah grant, tutup dan anggap sukses
      finish()
    } catch (e: Throwable) {
      showErrorDialog("Gagal memberikan izin via Shizuku: ${e.message}")
    }
  }

  private fun showShizukuCustomDialog(isPingOk: Boolean) {
    val d = android.app.Dialog(this).apply {
      requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
      setContentView(R.layout.dialog_shizuku)
      window?.applyDialogWindowStyle()
      setCancelable(false)
      setOnDismissListener { finish() }
    }

    val tvTitle = d.findViewById<android.widget.TextView>(R.id.tvShizukuTitle)
    val tvMessage = d.findViewById<android.widget.TextView>(R.id.tvShizukuMessage)
    val btnClose = d.findViewById<MaterialButton>(R.id.btnShizukuClose)
    val btnAllow = d.findViewById<MaterialButton>(R.id.btnShizukuAllow)

    tvTitle.text = "Dibutuhkan Shizuku"
    tvMessage.text = "Aplikasi ini membutuhkan Shizuku untuk berjalan pertama kali penginstalan."

    btnClose.setOnClickListener {
      vibrateClick()
      d.dismiss()
    }

    if (isPingOk) {
      btnAllow.visibility = android.view.View.VISIBLE
      btnAllow.setOnClickListener {
        vibrateClick()
        Shizuku.addRequestPermissionResultListener(this)
        Shizuku.requestPermission(SHIZUKU_CODE)
        d.setOnDismissListener(null)
        d.dismiss()
      }
    } else {
      btnAllow.visibility = android.view.View.GONE
      // Karena tombol Allow hilang, hapus marginEnd pada btnClose agar rata
      val lp = btnClose.layoutParams as android.widget.LinearLayout.LayoutParams
      lp.marginEnd = 0
      btnClose.layoutParams = lp
    }

    d.show()
  }

  private fun showErrorDialog(msg: String) {
    val d = android.app.Dialog(this).apply {
      requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
      setContentView(R.layout.dialog_shizuku)
      window?.applyDialogWindowStyle()
      setCancelable(false)
      setOnDismissListener { finish() }
    }

    val tvTitle = d.findViewById<android.widget.TextView>(R.id.tvShizukuTitle)
    val tvMessage = d.findViewById<android.widget.TextView>(R.id.tvShizukuMessage)
    val btnClose = d.findViewById<MaterialButton>(R.id.btnShizukuClose)
    val btnAllow = d.findViewById<MaterialButton>(R.id.btnShizukuAllow)

    tvTitle.text = "Gagal"
    tvMessage.text = msg
    btnAllow.visibility = android.view.View.GONE

    btnClose.setOnClickListener {
      vibrateClick()
      d.dismiss()
    }

    d.show()
  }

  override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
    if (requestCode == SHIZUKU_CODE) {
      Shizuku.removeRequestPermissionResultListener(this)
      if (grantResult == PackageManager.PERMISSION_GRANTED) {
        try {
          grantPermissionViaShizuku()
          Toast.makeText(this, "Izin diberikan, fitur terbuka.", Toast.LENGTH_SHORT).show()
          recreate() // Reload activity untuk melanjutkan flow normal
        } catch (e: Throwable) {
          Toast.makeText(this, "Gagal memberikan izin via Shizuku: ${e.message}", Toast.LENGTH_LONG).show()
          finish()
        }
      } else {
        finish()
      }
    }
  }

  protected fun android.view.Window.applyDialogWindowStyle() {
    setBackgroundDrawableResource(android.R.color.transparent)
    val density  = context.resources.displayMetrics.density
    val maxWidth = minOf(
      (DIALOG_MAX_DP * density).toInt(),
      context.resources.displayMetrics.widthPixels
    )
    setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT)
    setGravity(Gravity.CENTER)
    attributes = attributes.apply {
      dimAmount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.2f else 0.6f
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurBehindRadius = BLUR_RADIUS
    }
    addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }
  }

  protected fun vibrateClick() = VibrationHelper.click(this)
  protected fun vibrateTick()  = VibrationHelper.tick(this)

  private fun grantPermissionViaShizuku() {
    val pmBinder = SystemServiceHelper.getSystemService("package")
    val shizukuBinder = ShizukuBinderWrapper(pmBinder)
    val ipmClass = Class.forName("android.content.pm.IPackageManager\$Stub")
    val ipm = ipmClass.getDeclaredMethod("asInterface", android.os.IBinder::class.java).invoke(null, shizukuBinder)
    val grantMethod = ipm.javaClass.getMethod(
      "grantRuntimePermission", String::class.java, String::class.java, Int::class.javaPrimitiveType
    )
    grantMethod.invoke(ipm, packageName, WRITE_SECURE_SETTINGS, 0)
  }

  companion object {
    private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
    private const val DIALOG_MAX_DP = 320
    private const val BLUR_RADIUS   = 40
  }
}
