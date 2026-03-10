package com.aeldy24.restile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.PrintWriter
import java.io.StringWriter

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
  private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

  override fun uncaughtException(thread: Thread, throwable: Throwable) {
    // Ambil stacktrace menjadi string
    val sw = StringWriter()
    throwable.printStackTrace(PrintWriter(sw))
    val crashLog = sw.toString()

    // Karena ini thread yang crash, Toast dan ClipboardManager perlu Looper utama / background
    val worker = Thread {
      Looper.prepare()
      try {
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("ResTile_CrashLog", crashLog))
        Toast.makeText(context, "Aplikasi Crash! Log disalin ke clipboard.", Toast.LENGTH_LONG).show()
      } catch (e: Throwable) {
        // Abaikan jika sistem melarang akses clipboard saat crash
      }

      // Gunakan Handler untuk menghentikan Looper setelah waktu yang cukup untuk menampilkan Toast
      Handler(Looper.myLooper()!!).postDelayed({
        Looper.myLooper()?.quitSafely()
      }, 2000)

      Looper.loop()
    }
    worker.start()

    // Beri waktu sejenak agar Thread Toast/Clipboard selesai bekerja (sekitar 2 detik) sebelum aplikasi tertutup
    try {
      worker.join(2500)
    } catch (e: InterruptedException) {
      // Abaikan
    }

    // Lanjutkan ke handler bawaan sistem Android (munculkan dialog "App has stopped")
    defaultHandler?.uncaughtException(thread, throwable) ?: System.exit(1)
  }
}
