#!/usr/bin/env bash
# Undo everything: re-enable every package in debloat.txt plus the stock home, and make it Home again.
source "$(dirname "$0")/lib.sh"
parse_target "$@"
LIST="$(dirname "$0")/debloat.txt"
while read -r pkg; do installed "$pkg" && act pm enable "$pkg" || true; done < <(list_packages "$LIST")
for pkg in com.google.android.tungsten.setupwraith com.google.android.apps.tv.launcherx; do
  installed "$pkg" && act pm enable "$pkg" || true
done
act cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
act input keyevent KEYCODE_HOME
echo "Stock Google TV home restored$([ $APPLY = 1 ] || echo ' (dry run)')."
