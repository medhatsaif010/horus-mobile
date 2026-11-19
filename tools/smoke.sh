#!/bin/bash
# Installs the APK on the CI emulator, drives the main flows and saves screenshots + logs.
set -x
mkdir -p shots
P=com.horusict.shield
A=$P/.MainActivity
shot() { sleep "$2"; adb exec-out screencap -p > "shots/$1.png"; }
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb install -r app-debug.apk
adb logcat -c
adb shell am start -n $A
shot 0-splash 1
shot 1-home 7
adb shell am start -n $A --ez horus_autoscan true
shot 2-scanning 2
shot 3-result 25
adb shell input swipe 160 560 160 120 400
shot 4-result-2 2
adb shell input swipe 160 560 160 120 400
shot 5-result-3 2
adb shell input swipe 160 560 160 120 400
shot 6-result-4 2
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'عرض خاص http://nbe-eg.verify-account.top/login'" -n $A
sleep 4
adb shell input tap 160 287
shot 7-link-danger 18
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://www.google.com" -n $A
sleep 4
adb shell input tap 160 287
shot 8-link-ok 18
adb shell am start -n $A --es horus_lang en --es horus_screen home
shot 9-home-en 3
adb shell am start -n $A --ez horus_autoscan true
shot 10-result-en 15
adb shell am start -n $A --es horus_screen about
shot 11-about-en 3
adb shell am start -n $A --es horus_lang ar --es horus_screen about
shot 11b-about-ar 3
adb shell am start -n $A --es horus_screen legal
shot 11c-legal-ar 3
adb shell am start -n $A --es horus_lang eg --es horus_screen home
shot 11d-home-eg 3
adb shell am start -n $A --es horus_screen tools
shot 11e-tools-eg 3
adb shell am start -n $A --es horus_lang ar --es horus_screen settings
shot 12b-settings 3
adb shell input swipe 160 560 160 80 300
adb shell input swipe 160 560 160 80 300
shot 12c-settings-2 2
adb shell am start -n $A --es horus_screen tools
shot 12d-tools 3
adb shell am start -n $A --es horus_screen reports
shot 12e-reports 3
adb shell am start -n $A --es horus_lang ar --es horus_screen home --ez horus_fakeupdate true
shot 12-home-update 3
adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb shell am start -n $A --ez horus_testnotify true
sleep 2
adb shell am start -n $A --ez horus_testupdnotify true
sleep 3
adb shell cmd statusbar expand-notifications
shot 13-notification 3
adb shell cmd statusbar collapse
adb shell am start -n $A --ez horus_autoscan true --ez horus_deep false
shot 14-quick-result 12
adb shell am start -n $A --ez horus_rootmode true --es horus_screen settings
sleep 25
adb shell input swipe 160 560 160 80 300
adb shell input swipe 160 560 160 80 300
shot 16-rootcard 3
adb shell am start -n $A --ei horus_auto 3 --ez horus_runworker true --es horus_screen home
sleep 40
adb shell am force-stop $P
adb shell am start -n $A
shot 15-home-history 6
adb shell am start -n $A --es horus_screen settings
shot 15b-autoscan 3
adb shell am start -n $A --es horus_theme light --es horus_screen home
shot 18-home-light 3
adb shell am start -n $A --ez horus_autoscan true
shot 19-result-light 20
adb shell am start -n $A --es horus_screen settings
shot 19b-settings-light 3
adb shell am start -n $A --es horus_theme dark --es horus_screen link
shot 20-link-tab 3
adb shell am start -n $A --es horus_lang en --es horus_theme dark --es horus_screen ask
shot 22-ask-en 3
adb shell am start -n $A --es horus_ask "'my whatsapp got hacked'"
shot 22b-ask-en-answer 3
adb shell am start -n $A --es horus_lang eg --es horus_ask "'حد كلمني من البنك وطلب الكود'"
shot 22c-ask-eg 3
adb shell am start -n $A --es horus_lang ar --es horus_asktopic spyware-found
shot 22d-ask-ar-topic 3
# ---- user journey video for the website (English, dark) ----
adb shell am force-stop $P
adb shell am start -n $A --es horus_lang en --es horus_theme dark --es horus_screen home
sleep 4
adb shell screenrecord --bit-rate 6000000 --time-limit 150 /sdcard/journey.mp4 &
REC=$!
sleep 3
adb shell input tap 160 418          # Scan device
sleep 10
adb shell input swipe 160 500 160 260 900
sleep 3
adb shell input swipe 160 500 160 260 900
sleep 3
adb shell input swipe 160 500 160 260 900
sleep 3
adb shell input tap 30 52            # back to Home
sleep 3
adb shell input tap 120 600          # Tools tab
sleep 3
adb shell input tap 160 125          # Link check
sleep 3
adb shell input tap 160 222          # link field
sleep 1
adb shell input text "http://nbe-eg.verify-account.top/login"
sleep 2
adb shell input keyevent 111
sleep 1
adb shell input tap 160 287          # Check link
sleep 12
adb shell input tap 30 52            # back to Tools
sleep 3
adb shell input tap 200 600          # Reports tab
sleep 4
adb shell input tap 280 600          # Settings tab
sleep 4
adb shell input swipe 160 500 160 200 700
sleep 3
adb shell input tap 40 600           # Home tab
sleep 4
adb shell pkill -INT screenrecord || true
wait $REC || true
sleep 2
adb pull /sdcard/journey.mp4 shots/journey.mp4 || true
# ---- user journey video for the website (Arabic, dark) ----
adb shell am force-stop $P
adb shell am start -n $A --es horus_lang ar --es horus_theme dark --es horus_screen home
sleep 4
adb shell screenrecord --bit-rate 6000000 --time-limit 150 /sdcard/journey-ar.mp4 &
REC=$!
sleep 3
adb shell input tap 160 418          # Scan device
sleep 10
adb shell input swipe 160 500 160 260 900
sleep 3
adb shell input swipe 160 500 160 260 900
sleep 3
adb shell input swipe 160 500 160 260 900
sleep 3
adb shell input tap 290 52            # back to Home
sleep 3
adb shell input tap 200 600          # Tools tab
sleep 3
adb shell input tap 160 125          # Link check
sleep 3
adb shell input tap 160 175          # link field
sleep 1
adb shell input text "http://nbe-eg.verify-account.top/login"
sleep 2
adb shell input keyevent 111
sleep 1
adb shell input tap 160 243          # Check link
sleep 12
adb shell input tap 290 52            # back to Tools
sleep 3
adb shell input tap 120 600          # Reports tab
sleep 4
adb shell input tap 40 600           # Settings tab
sleep 4
adb shell input swipe 160 500 160 200 700
sleep 3
adb shell input tap 280 600          # Home tab
sleep 4
adb shell pkill -INT screenrecord || true
wait $REC || true
sleep 2
adb pull /sdcard/journey-ar.mp4 shots/journey-ar.mp4 || true
# ---- detection test: an app installed over adb that claims to come from Google Play ----
BT=$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)
PLAT=$(ls -d "$ANDROID_HOME"/platforms/android-*/ | sort -V | tail -1)
mkdir -p /tmp/spoof
cat > /tmp/spoof/AndroidManifest.xml <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.spooftest"><uses-permission android:name="android.permission.READ_SMS"/><application android:hasCode="false" android:label="Spoof Test"/></manifest>
XML
"${BT}aapt2" link -o /tmp/spoof/u.apk -I "${PLAT}android.jar" --manifest /tmp/spoof/AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 34 && \
"${BT}apksigner" sign --ks app/horus-test.keystore --ks-pass pass:android --out /tmp/spoof/spoof.apk /tmp/spoof/u.apk && \
adb install -i com.android.vending /tmp/spoof/spoof.apk
adb shell dumpsys package com.example.spooftest | grep -iE "installerPackageName|initiatingPackageName" > shots/spoof-install.txt || true
adb shell am start -n $A --es horus_lang en --ez horus_autoscan true
shot 23-spoof-result 20
adb shell input swipe 160 560 160 120 400
shot 23b-spoof-result 2
adb uninstall com.example.spooftest || true
# ---- new features: Egyptian fake apps, modified WhatsApp, before/after repair ----
adb shell am start -n $A --es horus_lang eg --ez horus_repairsave true --es horus_screen tools
shot 30-tools-eg 6
adb shell input swipe 160 560 160 160 500
shot 30b-tools-eg 2
mkapk() {  # $1 package  $2 label  $3 extra permission (optional)
  d=/tmp/t-$1; mkdir -p $d
  perm=""; [ -n "$3" ] && perm="<uses-permission android:name=\"$3\"/>"
  printf '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="%s">%s<application android:hasCode="false" android:label="%s"/></manifest>' "$1" "$perm" "$2" > $d/AndroidManifest.xml
  "${BT}aapt2" link -o $d/u.apk -I "${PLAT}android.jar" --manifest $d/AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 34 && \
  "${BT}apksigner" sign --ks app/horus-test.keystore --ks-pass pass:android --out $d/s.apk $d/u.apk && adb install $d/s.apk
}
mkapk com.example.fakepay "انستاباي" android.permission.READ_SMS
mkapk com.egyptianbanks.instapay "InstaPay Egypt"
mkapk com.gbwhatsapp "GBWhatsApp"
adb logcat -c
adb shell am start -n $A --es horus_lang en --ez horus_dump true
sleep 15
adb logcat -d -s HorusTest > shots/features.txt || true
cat shots/features.txt
adb shell am start -n $A --es horus_lang eg --es horus_screen bankapps
shot 31-bankapps-eg 6
adb shell am start -n $A --es horus_lang eg --ez horus_repaircompare true
shot 32-repair-eg 8
adb shell input swipe 160 560 160 160 500
shot 32b-repair-eg 2
adb shell am start -n $A --es horus_lang eg --es horus_screen whatsapp
shot 33-whatsapp-eg 3
adb shell input swipe 160 560 160 160 500
shot 33b-whatsapp-eg 2
adb shell am start -n $A --es horus_lang ar --es horus_screen trackers
shot 34-trackers-ar 3
adb shell am start -n $A --es horus_lang eg --es horus_screen help
shot 35-help-eg 3
adb shell input swipe 160 560 160 160 500
shot 35b-help-eg 2
adb shell am start -n $A --es horus_lang en --es horus_screen help
shot 35c-help-en 3
adb shell am start -n $A --es horus_lang eg --es horus_screen scamreport
shot 36-report-eg 3
adb shell am start -n $A --es horus_lang en --ez horus_autoscan true
shot 37-result-fakes 20
adb shell input swipe 160 560 160 120 400
shot 37b-result-fakes 2
adb uninstall com.example.fakepay || true
adb uninstall com.egyptianbanks.instapay || true
adb uninstall com.gbwhatsapp || true
# assertions: the build fails if a detection is missing
FAIL=0
need() { if grep -q "$1" shots/features.txt; then echo "PASS $1" >> shots/assert.txt; else echo "FAIL $1" >> shots/assert.txt; FAIL=1; fi; }
need "fake-finance:com.example.fakepay"
need "eg-official-outside:com.egyptianbanks.instapay"
need "whatsapp-mod:com.gbwhatsapp"
need "bankapp com.egyptianbanks.instapay OUTSIDE"
need "repair HIGH com.example.fakepay"
need "repair .* com.gbwhatsapp"
need "phone=01012345678"
need "bad=null"
need "dump-done"
cat shots/assert.txt
adb shell am start -n $A --ez horus_selftest true
sleep 6
adb logcat -d -s HorusSelfTest > shots/selftest.txt || true
adb shell dumpsys jobscheduler | grep -A3 "horusict" | head -20 > shots/jobs.txt
adb shell dumpsys package $P | grep -E "versionName|versionCode|targetSdk" > shots/package.txt
adb logcat -d -b crash | grep -iE "horusict|FATAL EXCEPTION" -A20 > shots/crash.txt || true
adb logcat -d | grep -iE "horusict|AndroidRuntime|FATAL" > shots/logcat.txt || true
pidof_out=$(adb shell pidof $P); echo "pid: $pidof_out" >> shots/package.txt
if grep -q "FATAL EXCEPTION" shots/crash.txt; then echo "FAIL crash" >> shots/assert.txt; FAIL=1; fi
exit $FAIL
