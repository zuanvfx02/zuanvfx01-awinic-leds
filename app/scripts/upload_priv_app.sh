#!/bin/bash

PKG=zuanvfx01.aw22xxx_leds

#./gradlew assembleDebug

#APK_SOURCE_PATH=./app/build/outputs/apk/debug/app-debug.apk
APK_SOURCE_PATH=./app/build/intermediates/apk/debug/app-debug.apk

APK_TARGET_DIR=/system/priv-app/AwinicLeds
APK_TARGET_PATH=$APK_TARGET_DIR/AwinicLeds.apk

adb shell "mount -o remount,rw /"

adb shell "mkdir -p $APK_TARGET_DIR"
adb shell "chmod 755 $APK_TARGET_DIR"

adb push $APK_SOURCE_PATH $APK_TARGET_PATH
adb shell "chmod 644 $APK_TARGET_PATH"

adb shell "am force-stop $PKG"
adb shell "nohup sh -c 'sleep 1 && am start -n \"$PKG/$PKG.MainActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER' >/dev/null 2>&1 & exit"