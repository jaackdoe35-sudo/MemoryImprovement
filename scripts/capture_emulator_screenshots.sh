#!/usr/bin/env bash
set -euo pipefail

echo "ADB devices after emulator startup:"
adb devices

echo "Verifying Android boot state with a 180-second timeout..."
elapsed=0
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do
  sleep 5
  elapsed=$((elapsed + 5))
  if [ "$elapsed" -ge 180 ]; then
    echo "Emulator failed to report boot completion within 180 seconds."
    adb devices
    adb version
    exit 1
  fi
done
echo "Emulator booted successfully."

mkdir -p docs/screenshots
adb shell wm size 1080x2400
adb shell settings put system font_scale 1.0
adb install -r app/build/outputs/apk/debug/app-debug.apk

capture() {
  local name="$1"
  adb exec-out screencap -p > "docs/screenshots/${name}.png"
  test -s "docs/screenshots/${name}.png"
}

adb shell cmd uimode night no
sleep 3
adb shell am force-stop com.memoryimprovement.app
adb shell am start -n com.memoryimprovement.app/.MainActivity
sleep 6
capture today-light
echo "Captured docs/screenshots/today-light.png"

# Second incremental capture: Today screen in dark mode.
adb shell cmd uimode night yes
sleep 3
adb shell am force-stop com.memoryimprovement.app
adb shell am start -n com.memoryimprovement.app/.MainActivity
sleep 6
capture today-dark
echo "Captured docs/screenshots/today-dark.png"
