package com.aeldy24.restile

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

data class DnsOption(
  val id: String,
  val label: String,
  val subtitle: String,
  val hostname: String?,  // null → ISP default (off)
  val iconRes: Int
)

object DnsManager {

  private const val TAG          = "DnsManager"
  private const val PREFS        = "dns_prefs"
  private const val KEY_ID       = "current_id"
  private const val DEFAULT_ID   = "off"
  private const val SETTING_MODE = "private_dns_mode"
  private const val SETTING_HOST = "private_dns_specifier"
  private const val MODE_HOSTNAME = "hostname"
  private const val MODE_OFF      = "off"

  val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

  val options: List<DnsOption> = listOf(
    DnsOption("off",        "Nonaktif",   "DNS bawaan ISP",       null,                  R.drawable.ic_provider_off),
    DnsOption("google",     "Google",     "dns.google",           "dns.google",          R.drawable.ic_provider_google),
    DnsOption("cloudflare", "Cloudflare", "one.one.one.one",      "one.one.one.one",     R.drawable.ic_provider_cloudflare),
    DnsOption("quad9",      "Quad9",      "dns.quad9.net",        "dns.quad9.net",       R.drawable.ic_provider_quad9),
    DnsOption("adguard",    "AdGuard",    "dns.adguard-dns.com",  "dns.adguard-dns.com", R.drawable.ic_provider_adguard),
  )

  fun apply(ctx: Context, opt: DnsOption): Boolean {
    check(isSupported) { "Private DNS requires Android 9+" }
    return try {
      val r = ctx.contentResolver
      if (opt.hostname == null) {
        Settings.Global.putString(r, SETTING_MODE, MODE_OFF)
      } else {
        Settings.Global.putString(r, SETTING_MODE, MODE_HOSTNAME)
        Settings.Global.putString(r, SETTING_HOST, opt.hostname)
      }
      saveId(ctx, opt.id)
      true
    } catch (e: Throwable) {
      Log.e(TAG, "apply '${opt.id}' failed", e)
      false
    }
  }

  fun syncFromSystem(ctx: Context) {
    if (!isSupported) return
    try {
      val r    = ctx.contentResolver
      val mode = Settings.Global.getString(r, SETTING_MODE) ?: MODE_OFF
      val host = Settings.Global.getString(r, SETTING_HOST) ?: ""
      val matched = if (mode == MODE_HOSTNAME) {
        options.find { it.hostname == host } ?: options.firstOrNull()
      } else {
        options.firstOrNull()
      }
      saveId(ctx, matched?.id ?: DEFAULT_ID)
    } catch (e: Throwable) {
      Log.w(TAG, "syncFromSystem failed", e)
    }
  }

  fun getCurrentId(ctx: Context): String =
    prefs(ctx).getString(KEY_ID, DEFAULT_ID) ?: DEFAULT_ID

  fun getCurrentOption(ctx: Context): DnsOption =
    options.find { it.id == getCurrentId(ctx) } ?: options.first()

  private fun prefs(ctx: Context) =
    ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun saveId(ctx: Context, id: String) {
    prefs(ctx).edit().putString(KEY_ID, id).apply()
  }
}
