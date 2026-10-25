#!/usr/bin/env bash
# Shared helpers. Every script talks to exactly ONE device, named explicitly, and changes nothing unless --apply.
set -euo pipefail

usage_target() {
  echo "usage: $0 <device> [--apply]" >&2
  echo "  <device>  adb serial, e.g. 192.168.0.175:5555 for the TV or emulator-5554 for the emulator" >&2
  echo "  --apply   actually make the changes (without it, the script only prints what it would do)" >&2
  exit 2
}

parse_target() {
  [ $# -ge 1 ] || usage_target
  DEVICE=$1; APPLY=0
  [ "${2:-}" = "--apply" ] && APPLY=1
  ADB=(adb -s "$DEVICE")
  "${ADB[@]}" get-state >/dev/null 2>&1 || { echo "Device $DEVICE is not connected (see README: pair and connect first)." >&2; exit 1; }
  MODEL=$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')
  echo "Device: $DEVICE ($MODEL)  —  mode: $([ $APPLY = 1 ] && echo APPLY || echo 'dry run (nothing will change)')"
}

# run a device command, or just print it in a dry run
act() {
  # </dev/null: adb shell would otherwise swallow the rest of the package list being read in a loop
  if [ $APPLY = 1 ]; then "${ADB[@]}" shell "$@" </dev/null; else echo "  would run: adb -s $DEVICE shell $*"; fi
}

installed() { "${ADB[@]}" shell pm list packages </dev/null | tr -d '\r' | sed 's/^package://' | grep -qx "$1"; }

list_packages() { sed 's/#.*//' "$1" | tr -d ' \t' | grep -v '^$'; }
