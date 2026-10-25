#!/usr/bin/env bash
# Disable the packages in debloat.txt (never uninstalls). Saves the TV's package state first, for restore.sh.
source "$(dirname "$0")/lib.sh"
parse_target "$@"
LIST="$(dirname "$0")/debloat.txt"
BACKUP="$(dirname "$0")/backup-$(echo "$DEVICE" | tr ':/' '__')-$(date +%Y%m%d-%H%M%S).txt"
if [ $APPLY = 1 ]; then
  "${ADB[@]}" shell pm list packages -d | tr -d '\r' > "$BACKUP.disabled-before"
  "${ADB[@]}" shell pm list packages | tr -d '\r' > "$BACKUP.all"
  echo "Backed up the package state to $BACKUP.*"
fi
n=0; skipped=0
while read -r pkg; do
  if installed "$pkg"; then act pm disable-user --user 0 "$pkg"; n=$((n + 1)); else skipped=$((skipped + 1)); fi
done < <(list_packages "$LIST")
echo "$n package(s) $([ $APPLY = 1 ] && echo disabled || echo 'would be disabled'), $skipped not on this device."
