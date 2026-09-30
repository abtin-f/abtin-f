#!/usr/bin/env bash
# Runs inside the emulator job: installs the APK, walks through the main screens, saves screenshots.
mkdir -p out
adb install -r apk/release/app-release.apk 2>&1 | tee out/install.txt
adb shell pm grant com.abtinf.glassmusic android.permission.READ_MEDIA_AUDIO || true
adb shell pm grant com.abtinf.glassmusic android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -n com.abtinf.glassmusic/.MainActivity
sleep 10
shot() { adb exec-out screencap -p > out/$1.png; }
shot 01_home
python3 ci/tap.py "Library"; sleep 2; shot 02_library
python3 ci/tap.py "Songs"; sleep 2; shot 03_songs
python3 ci/tap.py "Bad Decisions"; sleep 2; shot 04_metadata
python3 ci/tap.py "Play" 0; sleep 3
adb shell input keyevent 4; sleep 2; shot 05_mini_player
adb shell input swipe 540 2040 540 500 300; sleep 3; shot 06_player
python3 ci/tap.py "More"; sleep 2; shot 07_menu
adb shell input tap 540 2250; sleep 1
python3 ci/tap.py "Lyrics"; sleep 2; shot 08_lyrics
python3 ci/tap.py "Queue"; sleep 2; shot 09_queue
python3 ci/tap.py "Output"; sleep 2; shot 10_output
adb logcat -d -b crash > out/crash.txt
adb logcat -d -s AndroidRuntime:E ActivityManager:I > out/runtime.txt
adb shell pidof com.abtinf.glassmusic > out/pid.txt || echo "NOT RUNNING" > out/pid.txt
