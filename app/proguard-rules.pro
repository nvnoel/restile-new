# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Shizuku
-keep class rikka.shizuku.** { *; }

# HiddenApiBypass
-keep class org.lsposed.hiddenapibypass.** { *; }

# Keep DnsManager, ResolutionManager, etc., from being minified entirely if used via reflection or intents
-keep class com.aeldy24.restile.** { *; }

# Keep Android internal classes that might be accessed via reflection
-keep class android.os.IWindowManager { *; }
-keep class android.os.IWindowManager$Stub { *; }
-keep class android.os.ServiceManager { *; }
-keep class android.view.IWindowManager { *; }
-keep class android.view.IWindowManager$Stub { *; }

-dontwarn rikka.shizuku.**
-dontwarn org.lsposed.hiddenapibypass.**
-dontwarn android.os.IWindowManager
-dontwarn android.view.IWindowManager
