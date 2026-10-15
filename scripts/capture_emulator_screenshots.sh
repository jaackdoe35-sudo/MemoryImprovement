#!/usr/bin/env bash
set -euo pipefail

on_exit() {
  local status=$?
  trap - EXIT
  if [ "$status" -ne 0 ]; then
    echo "Screenshot capture failed (exit $status). Recent Android crash/runtime logs:"
    adb logcat -d -t 500 -v brief 2>/dev/null | grep -E 'FATAL EXCEPTION|AndroidRuntime|Process: com\.memoryimprovement\.app|com\.memoryimprovement\.app.*(Exception|Error)' | tail -n 120 || true
    echo "ADB state:"
    adb devices || true
    adb shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity' | tail -n 10 || true
  fi
  exit "$status"
}
trap on_exit EXIT

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
  echo "Captured docs/screenshots/${name}.png"
}

launch_app() {
  adb shell am force-stop com.memoryimprovement.app
  adb shell am start -W -n com.memoryimprovement.app/.MainActivity
  sleep 5
  if ! adb shell pidof com.memoryimprovement.app 2>/dev/null | grep -q '[0-9]'; then
    echo "App process is not running after launch; refusing to save a potentially misleading screenshot."
    adb logcat -d -t 500 -v brief | tail -n 200 || true
    return 1
  fi
}

tap_label() {
  local label="$1"
  local coords
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null
  coords="$(python3 scripts/tap_text.py "$label")"
  read -r x y <<< "$coords"
  adb shell input tap "$x" "$y"
  sleep 3
}

capture_tabs() {
  local theme="$1"
  capture "today-${theme}"

  tap_label "Palace"
  capture "palace-${theme}"

  tap_label "Recall"
  capture "recall-${theme}"

  tap_label "Library"
  capture "library-${theme}"

  tap_label "Today"
  tap_label "Add a first fact"
  capture "add-memory-${theme}"
  tap_label "Cancel"
}

adb shell cmd uimode night no
sleep 3
launch_app
capture_tabs light

adb shell cmd uimode night yes
sleep 3
launch_app
capture_tabs dark

python3 - <<'PY'
from pathlib import Path
import struct
import sys

names = [
    "today-light", "palace-light", "recall-light", "library-light", "add-memory-light",
    "today-dark", "palace-dark", "recall-dark", "library-dark", "add-memory-dark",
]
errors = []
for name in names:
    path = Path("docs/screenshots") / f"{name}.png"
    if not path.is_file():
        errors.append(f"{path}: missing")
        continue
    data = path.read_bytes()
    if len(data) < 1000:
        errors.append(f"{path}: unexpectedly small ({len(data)} bytes)")
        continue
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        errors.append(f"{path}: invalid PNG signature")
        continue
    if len(data) < 24 or data[12:16] != b"IHDR":
        errors.append(f"{path}: missing PNG IHDR header")
        continue
    width, height = struct.unpack(">II", data[16:24])
    if (width, height) != (1080, 2400):
        errors.append(f"{path}: expected 1080x2400, got {width}x{height}")
    print(f"Validated {path}: {width}x{height}, {len(data)} bytes")

if errors:
    print("Screenshot validation failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print(f"Validated all {len(names)} screenshots.")
PY


echo "All 10 expected light/dark screen captures completed."
