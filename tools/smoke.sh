#!/bin/bash
# Installs the APK on the CI emulator, drives the main flows and saves screenshots + logs.
set -x
mkdir -p shots
P=com.horussec.mobile
A=$P/.MainActivity
shot() { sleep "$2"; adb exec-out screencap -p > "shots/$1.png"; }
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb install -r app-debug.apk
adb logcat -c
adb shell am start -n $A
shot 1-home 8
adb shell am start -n $A --ez horus_autoscan true
shot 2-scanning 1
shot 3-result 14
adb shell input swipe 160 560 160 120 400
shot 4-result-2 2
adb shell input swipe 160 560 160 120 400
shot 5-result-3 2
adb shell input swipe 160 560 160 120 400
shot 6-result-4 2
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'عرض خاص http://nbe-eg.verify-account.top/login'" -n $A
shot 7-link-danger 20
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://www.google.com" -n $A
shot 8-link-ok 20
adb shell am start -n $A --es horus_lang en --es horus_screen home
shot 9-home-en 3
adb shell am start -n $A --ez horus_autoscan true
shot 10-result-en 15
adb shell am start -n $A --es horus_screen about
shot 11-about-en 3
adb shell am start -n $A --es horus_lang ar --es horus_screen home --ez horus_fakeupdate true
shot 12-home-update 3
adb shell dumpsys package $P | grep -E "versionName|versionCode|targetSdk" > shots/package.txt
adb logcat -d -b crash | grep -iE "horussec|FATAL EXCEPTION" -A20 > shots/crash.txt || true
adb logcat -d | grep -iE "horussec|AndroidRuntime|FATAL" > shots/logcat.txt || true
pidof_out=$(adb shell pidof $P); echo "pid: $pidof_out" >> shots/package.txt
exit 0
