#!/usr/bin/env bash
# Measures APK size and cold-start time for the full and lite editions.
#
# Needs: Android SDK (ANDROID_HOME), a JDK, and ONE connected device or emulator (`adb devices`).
# Usage: tools/measure.sh [runs]      (default 10 cold starts per scenario)
#
# Cold start = `adb shell am start -W` "TotalTime" (ms) after `am force-stop`. It depends on the
# device, its load and thermal state: only compare numbers measured in the same session.
set -euo pipefail
cd "$(dirname "$0")/.."
RUNS="${1:-10}"

echo "== Building =="
./gradlew assembleFullDebug assembleLiteDebug assembleFullRelease assembleLiteRelease

apk() { ls app/build/outputs/apk/"$1"/"$2"/*.apk | head -1; }
size() { stat -c %s "$1" 2>/dev/null || stat -f %z "$1"; }

echo
echo "== APK size (bytes) =="
for flavor in full lite; do
  for type in debug release; do
    f="$(apk "$flavor" "$type")"
    printf '%-6s %-8s %10s  %s\n' "$flavor" "$type" "$(size "$f")" "$f"
  done
done
echo "(release APKs are unsigned and minified; debug APKs are not minified)"

median() { sort -n | awk '{a[NR]=$1} END {print (NR%2)?a[(NR+1)/2]:(a[NR/2]+a[NR/2+1])/2}'; }

cold_start() { # package component
  local pkg="$1" component="$2" i out
  local times=()
  for ((i = 0; i < RUNS; i++)); do
    adb shell am force-stop "$pkg"
    sleep 1
    out="$(adb shell am start -W -n "$component" | tr -d '\r')"
    times+=("$(echo "$out" | awk '/TotalTime/ {print $2}')")
  done
  printf '%s\n' "${times[@]}" | median
}

echo
echo "== Cold start, median of $RUNS runs (ms, TotalTime) =="
FULL_PKG=com.fluxie.browser
LITE_PKG=com.fluxie.browser.lite

adb install -r -t "$(apk full debug)" >/dev/null
adb shell pm clear "$FULL_PKG" >/dev/null
echo "full edition, Normal mode : $(cold_start "$FULL_PKG" "$FULL_PKG/.MainActivity")"

# Put the full edition into Lite mode by writing the launch flag (debug build only: uses run-as).
LITE_FLAG="$(mktemp)"
cat > "$LITE_FLAG" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="lite_mode" value="true" />
</map>
XML
adb shell "run-as $FULL_PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/config.xml'" < "$LITE_FLAG"
rm -f "$LITE_FLAG"
adb shell am force-stop "$FULL_PKG"
echo "full edition, Lite mode   : $(cold_start "$FULL_PKG" "$FULL_PKG/.MainActivity")"

adb install -r -t "$(apk lite debug)" >/dev/null
adb shell pm clear "$LITE_PKG" >/dev/null
echo "lite edition              : $(cold_start "$LITE_PKG" "$LITE_PKG/com.fluxie.browser.lite.LiteActivity")"
