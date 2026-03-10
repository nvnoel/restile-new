package com.aeldy24.restile

import android.app.Application

class ResTileApp : Application() {
  override fun onCreate() {
    super.onCreate()
    Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
  }
}
