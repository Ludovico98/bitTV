#!/usr/bin/env bash
# Install BitTV and make it the TV's home screen, in the order cleanyourtv found safe on the G08 platform:
#   1. install BitTV and open it   2. setupwraith off (only if it doesn't own the Wi-Fi)   3. stock home off.
# Undo with restore.sh.
source "$(dirname "$0")/lib.sh"
parse_target "$@"
APK="$(dirname "$0")/../../android/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "Build it first: (cd android && ./gradlew :app:assembleRelease)" >&2; exit 1; }
if [ $APPLY = 1 ]; then "${ADB[@]}" install -r "$APK"; else echo "  would run: adb -s $DEVICE install -r $APK"; fi
act am start -n com.bittv.home/.MainActivity
# Google TV's setup app claims HOME once the stock launcher is off. If it owns the saved Wi-Fi network, disabling it
# can drop the TV off Wi-Fi: re-join the network in Settings first (cleanyourtv: disable.sh skips it in that case).
if installed com.google.android.tungsten.setupwraith; then
  if "${ADB[@]}" shell dumpsys wifi 2>/dev/null | tr -d '\r' | grep -q "setupwraith"; then
    echo "  ! setupwraith owns a saved Wi-Fi network: forget and re-join the Wi-Fi in Settings, then run this again."
    exit 1
  fi
  act pm disable-user --user 0 com.google.android.tungsten.setupwraith
fi
installed com.google.android.apps.tv.launcherx && act pm disable-user --user 0 com.google.android.apps.tv.launcherx
act cmd package set-home-activity com.bittv.home/.MainActivity
act input keyevent KEYCODE_HOME
echo "$([ $APPLY = 1 ] && echo 'BitTV is now the home screen.' || echo 'Dry run: BitTV would become the home screen.') Undo: tools/tv/restore.sh $DEVICE --apply"
