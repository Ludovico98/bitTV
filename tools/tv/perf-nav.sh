#!/usr/bin/env bash
# Frame times while navigating Home: the same presses every run (4× Right, 4× Left, Down, Up), then gfxinfo's
# percentiles and framestats' average phase split. Start with BitTV on Home, banner or first row focused.
# Only presses the remote's arrows (nothing is changed on the device), so it needs no --apply.
#   tools/tv/perf-nav.sh 192.168.0.175:5555
source "$(dirname "$0")/lib.sh"
parse_target "$@"
PKG=com.bittv.home
# the screensaver may be up: end it first (WAKEUP does nothing to a screen already awake)
"${ADB[@]}" shell input keyevent KEYCODE_WAKEUP; sleep 1.5
"${ADB[@]}" shell dumpsys gfxinfo $PKG reset >/dev/null
"${ADB[@]}" shell 'for i in 1 2 3 4; do input keyevent 22; sleep 0.4; done
  for i in 1 2 3 4; do input keyevent 21; sleep 0.4; done
  input keyevent 20; sleep 0.7; input keyevent 19; sleep 0.7'
"${ADB[@]}" shell dumpsys gfxinfo $PKG | tr -d '\r' | grep -E '^(Total frames|Janky frames:|50th percentile|90th percentile)'
# framestats: only frames with Flags=0 (real frames, not the first after a reset); times in ns
"${ADB[@]}" shell dumpsys gfxinfo $PKG framestats | tr -d '\r' | awk -F, '
  /---PROFILEDATA---/ { p = !p; next }
  p && $1 == "Flags" { for (i = 1; i <= NF; i++) h[$i] = i; next }
  p && $1 == 0 && $(h["FrameCompleted"]) > 0 && $(h["SwapBuffers"]) > 0 {
    n++
    ui    += ($(h["PerformTraversalsStart"]) - $(h["HandleInputStart"])) / 1e6
    rec   += ($(h["SyncStart"]) - $(h["PerformTraversalsStart"])) / 1e6
    issue += ($(h["SwapBuffers"]) - $(h["IssueDrawCommandsStart"])) / 1e6
    swap  += ($(h["FrameCompleted"]) - $(h["SwapBuffers"])) / 1e6
    tot   += ($(h["FrameCompleted"]) - $(h["IntendedVsync"])) / 1e6
  }
  END { if (n) printf "framestats (%d frames, avg ms): input+anim %.1f | layout/record %.1f | issue %.1f | swap (GPU wait) %.1f | total %.1f\n", n, ui/n, rec/n, issue/n, swap/n, tot/n }'
