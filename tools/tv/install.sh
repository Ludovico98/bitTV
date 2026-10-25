#!/usr/bin/env bash
# Install BitTV and make it the TV's home screen, in the order cleanyourtv found safe on the G08 platform:
#   1. install BitTV and open it   2. permissions and the Home helper   3. BitTV as the preferred home.
# Undo with restore.sh.
source "$(dirname "$0")/lib.sh"
parse_target "$@"
APK="$(dirname "$0")/../../android/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "Build it first: (cd android && ./gradlew :app:assembleRelease)" >&2; exit 1; }
if [ $APPLY = 1 ]; then "${ADB[@]}" install -r "$APK"; else echo "  would run: adb -s $DEVICE install -r $APK"; fi
act am start -n com.bittv.home/.MainActivity
# Google TV's own home (launcherx) stays ENABLED: Google Assistant refuses to work without it ("Sorry, I'm not
# available in this profile yet", found on the TV 2026-10-25). BitTV's HomeKeyService (accessibility) takes the Home
# button and replaces Google's home screen whenever it appears.
act pm enable com.google.android.apps.tv.launcherx
act cmd notification allow_listener com.bittv.home/com.bittv.home.platform.MediaListener   # Spotify card: what's playing
act pm grant com.bittv.home android.permission.RECORD_AUDIO                                 # Search: Speak
# The Home helper: Android 13+ only lets a sideloaded app's accessibility service run once "restricted settings" are
# allowed for it (Settings → Apps → BitTV → ⋮ → Allow restricted settings, or the appops line below). Your call.
echo "  To enable the Home helper:  adb -s $DEVICE shell cmd appops set com.bittv.home ACCESS_RESTRICTED_SETTINGS allow"
CUR="$("${ADB[@]}" shell settings get secure enabled_accessibility_services </dev/null | tr -d '\r')"
SVC=com.bittv.home/com.bittv.home.platform.HomeKeyService
case "$CUR" in *"$SVC"*) NEW="$CUR";; null|"") NEW="$SVC";; *) NEW="$CUR:$SVC";; esac
act settings put secure enabled_accessibility_services "$NEW"
act settings put secure accessibility_enabled 1
act cmd package set-home-activity com.bittv.home/.MainActivity
act input keyevent KEYCODE_HOME
echo "$([ $APPLY = 1 ] && echo 'BitTV is now the home screen.' || echo 'Dry run: BitTV would become the home screen.') Undo: tools/tv/restore.sh $DEVICE --apply"
