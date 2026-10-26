#!/usr/bin/env bash
# Windows (Git Bash / MSYS2) undo over plain adb: re-enable every package in a debloat list, turn off
# the launcher's parental-control service and make a stock launcher the home screen again.
# Usage: tools/restore-windows.sh versoes/debloat-v1.txt
#   STOCK_LAUNCHER=com.google.android.tvlauncher/.MainActivity (default; Google TV: launcherx)
# The TV's IP is read from privado/tv-ip.txt. One pm call per adb command; chained calls can stop early.
set -euo pipefail
cd "$(dirname "$0")/.."
LIST="${1:?usage: tools/restore-windows.sh <debloat list>}"
STOCK_LAUNCHER="${STOCK_LAUNCHER:-com.google.android.tvlauncher/.MainActivity}"
TV="$(tr -d '[:space:]' < privado/tv-ip.txt):5555"
export MSYS_NO_PATHCONV=1
adb connect "$TV" >/dev/null
for p in $(grep -vE '^\s*(#|$)' "$LIST" | awk '{print $1}'); do
  adb -s "$TV" shell pm enable "$p"
done
adb -s "$TV" shell pm enable "${STOCK_LAUNCHER%%/*}"
adb -s "$TV" shell settings put secure enabled_accessibility_services null
adb -s "$TV" shell settings put secure accessibility_enabled 0
adb -s "$TV" shell cmd package set-home-activity --user 0 "$STOCK_LAUNCHER"
adb -s "$TV" shell input keyevent KEYCODE_HOME
echo "Restored. The tv-home launcher is still installed: adb uninstall com.mihir.tvhome to remove it."
