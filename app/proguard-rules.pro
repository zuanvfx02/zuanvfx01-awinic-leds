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


# Zuan Awinic LED's DataStore uses protobuf-lite generated Settings.
# Keep generated protobuf message classes intact if minification is enabled
# again in the future; the current release build disables R8 as a workaround.
-keep class zuanvfx01.aw22xxx_leds.Settings { *; }
-keep class zuanvfx01.aw22xxx_leds.SavedLedSettings { *; }
-keep class zuanvfx01.aw22xxx_leds.AutomationConfig { *; }
-keep class zuanvfx01.aw22xxx_leds.TimerSettings { *; }
-keep class zuanvfx01.aw22xxx_leds.MusicLedConfig { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}