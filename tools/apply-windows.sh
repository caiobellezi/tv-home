#!/usr/bin/env bash
# Windows (Git Bash / MSYS2) apply over plain adb: disable every package in a debloat list, one at a
# time, then make the tv-home launcher the home screen and turn its parental-control service on.
# Run launcher/build-windows.sh --install first. Undo with tools/restore-windows.sh <same list>.
# Usage: tools/apply-windows.sh versoes/debloat-v1.txt
set -euo pipefail
cd "$(dirname "$0")/.."
LIST="${1:?usage: tools/apply-windows.sh <debloat list>}"
TV="$(tr -d '[:space:]' < privado/tv-ip.txt):5555"
export MSYS_NO_PATHCONV=1
adb connect "$TV" >/dev/null
adb -s "$TV" shell pm path com.mihir.tvhome >/dev/null || { echo "Install the launcher first: launcher/build-windows.sh --install"; exit 1; }
for p in $(grep -vE '^\s*(#|$)' "$LIST" | awk '{print $1}'); do
  adb -s "$TV" shell pm disable-user --user 0 "$p"
done
adb -s "$TV" shell cmd package set-home-activity --user 0 com.mihir.tvhome/.MainActivity
GUARD=com.mihir.tvhome/com.mihir.tvhome.Guard
CUR="$(adb -s "$TV" shell settings get secure enabled_accessibility_services | tr -d '\r')"
case "$CUR" in
  *"$GUARD"*) ;;
  ""|null) adb -s "$TV" shell settings put secure enabled_accessibility_services "$GUARD" ;;
  *) adb -s "$TV" shell settings put secure enabled_accessibility_services "$CUR:$GUARD" ;;
esac
adb -s "$TV" shell settings put secure accessibility_enabled 1
adb -s "$TV" shell input keyevent KEYCODE_HOME
echo "Applied. Press Home on the remote."
