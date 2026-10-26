#!/usr/bin/env bash
# Windows (Git Bash / MSYS2) version of build-and-install.sh for an Android TV 10 set (API 29).
# Builds with the bare SDK tools (no Gradle) and installs over plain adb.
#   launcher/build-windows.sh --build      build only
#   launcher/build-windows.sh --install    build + install, but leave the stock launcher as home (test mode)
#   launcher/build-windows.sh --set-home   build + install + make this launcher the default home;
#                                          the stock launcher stays enabled, so it can be switched back
# Needs JDK 17 (JAVA_HOME) and the Android SDK with platforms;android-34 and build-tools;34.0.0
# (ANDROID_HOME, default %LOCALAPPDATA%\Android\Sdk). The TV's IP is read from privado/tv-ip.txt.
set -euo pipefail
cd "$(dirname "$0")"
MODE="${1:---build}"
: "${JAVA_HOME:?set JAVA_HOME to a JDK 17}"
# "C:/..." would split PATH at the drive colon, so add the POSIX form of the JDK's bin folder.
export PATH="$(cygpath -u "$JAVA_HOME")/bin:$PATH"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
MIN_API=29
B=build

rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$B/dex"
"$BT/aapt2" compile --dir res -o "$B/res.zip"
"$BT/aapt2" link -o "$B/base.apk" -I "$JAR" --manifest AndroidManifest.xml --java "$B/gen" \
  -A assets --min-sdk-version $MIN_API --target-sdk-version 30 "$B/res.zip"
javac -nowarn -Xlint:-options --release 11 -encoding UTF-8 -classpath "$JAR" -d "$B/classes" \
  $(find src "$B/gen" -name '*.java')
"$BT/d8.bat" --min-api $MIN_API --lib "$JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')
# No zip on Windows: rewrite the APK with Python's zipfile (keeping each entry's compression, so
# resources.arsc stays stored) and add classes.dex. Appending in place gives zipalign header mismatches.
python -I -c "
import sys, zipfile
src, dst, dex = sys.argv[1:4]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w') as zout:
    for info in zin.infolist():
        zout.writestr(info, zin.read(info))
    zout.write(dex, 'classes.dex', zipfile.ZIP_DEFLATED)
" "$B/base.apk" "$B/unsigned.apk" "$B/dex/classes.dex"
"$BT/zipalign" -f 4 "$B/unsigned.apk" "$B/aligned.apk"
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias tvhome -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tvhome" >/dev/null 2>&1
"$BT/apksigner.bat" sign --ks debug.keystore --ks-pass pass:android --out "$B/tvhome.apk" "$B/aligned.apk"
echo "built $B/tvhome.apk ($(wc -c < "$B/tvhome.apk") bytes)"
[ "$MODE" = "--build" ] && exit 0

TV="$(tr -d '[:space:]' < ../privado/tv-ip.txt):5555"
adb connect "$TV" >/dev/null
adb -s "$TV" install -r "$B/tvhome.apk"
# Android 10 hides the Wi-Fi name (and reports no network) to apps without location.
adb -s "$TV" shell pm grant com.mihir.tvhome android.permission.ACCESS_FINE_LOCATION
# Family calendar on the locked screen, and the kids' timer returning to it over other apps.
adb -s "$TV" shell pm grant com.mihir.tvhome android.permission.READ_CALENDAR
adb -s "$TV" shell pm grant com.mihir.tvhome android.permission.WRITE_CALENDAR
adb -s "$TV" shell pm grant com.mihir.tvhome android.permission.RECORD_AUDIO
# Guard (accessibility service): add it to the enabled list, keeping any service already there.
GUARD=com.mihir.tvhome/com.mihir.tvhome.Guard
CUR="$(adb -s "$TV" shell settings get secure enabled_accessibility_services | tr -d '\r')"
case "$CUR" in
  *"$GUARD"*) ;;
  ""|null) adb -s "$TV" shell settings put secure enabled_accessibility_services "$GUARD" ;;
  *) adb -s "$TV" shell settings put secure enabled_accessibility_services "$CUR:$GUARD" ;;
esac
adb -s "$TV" shell settings put secure accessibility_enabled 1
adb -s "$TV" shell appops set com.mihir.tvhome SYSTEM_ALERT_WINDOW allow
if [ "$MODE" = "--set-home" ]; then
  MSYS_NO_PATHCONV=1 adb -s "$TV" shell cmd package set-home-activity --user 0 com.mihir.tvhome/.MainActivity
  adb -s "$TV" shell input keyevent KEYCODE_HOME
  echo "Installed and set as home. Back to stock: adb shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity"
else
  MSYS_NO_PATHCONV=1 adb -s "$TV" shell am start -n com.mihir.tvhome/.MainActivity
  echo "Installed; the Home setting was not changed."
fi
