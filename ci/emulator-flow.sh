#!/usr/bin/env bash
# Runs inside the emulator job: installs the optimised APK and walks through every screen, taking screenshots.
APK=${1:-apk/release/app-release.apk}
PKG=com.abtinf.glassmusic
mkdir -p out
# Let the freshly booted emulator settle (system apps hog the CPU for the first ~30s and cause bogus ANRs).
sleep 40
# Suppress system ANR/crash dialogs (e.g. "Pixel Launcher isn't responding") that would cover the app.
adb shell settings put global hide_error_dialogs 1
adb shell settings put global window_animation_scale 1
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null
adb install -r "$APK" 2>&1 | tee out/install.txt
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO || true
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

n=0
shot() { n=$((n+1)); name=$(printf "%02d_%s" $n "$1"); adb exec-out screencap -p > out/$name.png
  if adb shell pidof $PKG > /dev/null; then echo "$name ALIVE" >> out/steps.txt; else echo "$name DEAD" >> out/steps.txt; fi; }
tap() { python3 ci/tap.py "$1" ${2:-0} | tee -a out/taps.txt; sleep ${3:-2}; }
back() { adb shell input keyevent 4; sleep 1.5; }
fresh() { adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null; adb shell am force-stop $PKG; sleep 1; adb shell am start -n $PKG/.MainActivity > /dev/null; sleep 9; }

# --- A: navigation -------------------------------------------------------------------------
fresh
shot home
tap "Tame Impala"; shot artist_detail; back
tap "Library"; shot library
tap "Playlists"; shot playlists; back
tap "Artists"; shot artists; back
tap "Albums"; shot albums; back
tap "Folders"; shot folders
tap "Search"; shot search
adb shell input tap 540 420; sleep 1; adb shell input text tame; sleep 2; shot search_results
tap "Library" 0 0.25; shot tab_lens_midtap
sleep 2; tap "Home"; shot home_again

# --- B: songs list, filters, playlist editor -------------------------------------------------
fresh
tap "Library"; tap "Songs"; shot songs
adb shell dumpsys gfxinfo $PKG reset > /dev/null
for i in 1 2 3; do adb shell input swipe 540 1700 540 700 250; sleep 0.6; done
adb shell dumpsys gfxinfo $PKG > out/gfxinfo_scroll.txt
shot songs_scrolled
fresh
tap "Library"; tap "Songs"
tap "Filters"; shot filters_menu; back
tap "Create playlist" 0 3; shot playlist_editor

# --- C: metadata + player -----------------------------------------------------------------
fresh
tap "Library"; tap "Songs"
tap "Bad Decisions"; shot metadata
tap "Play" 0 3; shot metadata_playing
back; sleep 1
adb shell input tap 400 2040; sleep 3; shot player_expanded
sleep 3; shot player_playing
# uiautomator cannot dump while the player animates, so use fixed coordinates (1080x2400 screen)
adb shell input tap 959 1285; sleep 2; shot player_menu
back; sleep 1.5
adb shell input tap 184 2246; sleep 2.5; shot lyrics
adb shell input tap 897 2246; sleep 2.5; shot queue
adb shell input tap 184 2246; sleep 1.5
adb shell input tap 540 2246; sleep 2.5; shot output
back; sleep 1.5
adb shell input swipe 540 160 540 1800 300; sleep 2; shot player_collapsed

# --- D: real songs + system media controls (notification / media keys) ---------------------------
mkdir -p /tmp/music && python3 ci/make_audio.py /tmp/music
adb shell mkdir -p /sdcard/Music/GlassTest
for f in /tmp/music/*.wav; do
  adb push "$f" /sdcard/Music/GlassTest/ > /dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Music/GlassTest/$(basename "$f")" > /dev/null
done
sleep 8
adb shell content query --uri content://media/external/audio/media --projection _id:title:is_music:duration > out/mediastore.txt 2>&1
adb logcat -c
fresh
shot real_home
tap "Library"; tap "Songs"; shot real_songs
tap "GlassTestA"; shot real_song_opened
tap "Play" 0 4
ms() {
  adb shell dumpsys media_session > "out/ms_$1.txt" 2>&1
  echo "$1 | $(grep -m1 -o 'description=[^,]*' "out/ms_$1.txt") | $(grep -m1 -o 'state=PlaybackState {[^}]*}' "out/ms_$1.txt" | cut -c1-120)" >> out/ms_summary.txt
}
adb shell input keyevent 3; sleep 4; shot real_home_screen_playing; ms 0_playing
adb shell input keyevent 87; sleep 2; ms 1_after_next
adb shell input keyevent 88; sleep 1; ms 2_after_prev_quick
adb shell input keyevent 87; sleep 2; ms 3_next_again
adb shell input keyevent 87; sleep 2; ms 4_next_again
adb shell input keyevent 85; sleep 2; ms 5_after_playpause
adb shell input keyevent 85; sleep 2; ms 6_after_playpause2
adb shell cmd statusbar expand-notifications; sleep 3; shot shade
adb shell uiautomator dump /sdcard/shade.xml > /dev/null 2>&1; adb pull /sdcard/shade.xml out/shade_ui.xml > /dev/null 2>&1
python3 ci/tap.py "Next track" | tee -a out/taps.txt; sleep 3; shot shade_after_next_tap; ms 7_after_shade_next
python3 ci/tap.py "Previous track" | tee -a out/taps.txt; sleep 2; ms 8_after_shade_prev
adb logcat -d | grep -iE "media3|MediaSession|PlayerController|glassmusic|AndroidRuntime|GlassSession|GlassPlayer" | tail -400 > out/media_log.txt
adb shell cmd statusbar collapse

adb logcat -d -b crash > out/crash.txt
adb logcat -d -s AndroidRuntime:E ActivityManager:I > out/runtime.txt
adb shell pidof $PKG > out/pid.txt || echo "NOT RUNNING" > out/pid.txt
