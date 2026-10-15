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
capture_profile() {
  local suffix="$1" size="$2" density="$3" scale="$4"
  DEVICE_SUFFIX="$suffix"
  echo "Capturing profile $suffix (size=$size density=$density font_scale=$scale)"
  adb shell wm size "$size"
  adb shell wm density "$density"
  adb shell settings put system font_scale "$scale"
  for theme in light dark; do
    if [ "$theme" = "light" ]; then adb shell cmd uimode night no; else adb shell cmd uimode night yes; fi
    sleep 2
    launch_app
    capture_tabs "$theme"
  done
}

# Match the requested logical dimensions exactly: 360x800 dp phone and 840x1280 dp tablet.
capture_profile phone-100 1080x2400 480 1.0
capture_profile phone-200 1080x2400 480 2.0
capture_profile tablet-100 1680x2560 320 1.0
capture_profile tablet-200 1680x2560 320 2.0

python3 - <<'PY'
from pathlib import Path
import struct
import sys

screens = ["today", "palace", "recall", "library", "add-memory"]
themes = ["light", "dark"]
profiles = {
    "phone-100": (1080, 2400),
    "phone-200": (1080, 2400),
    "tablet-100": (1680, 2560),
    "tablet-200": (1680, 2560),
}
errors = []
count = 0
for profile, expected_size in profiles.items():
    for theme in themes:
        for screen in screens:
            path = Path("docs/screenshots") / f"{screen}-{theme}-{profile}.png"
            if not path.is_file():
                errors.append(f"{path}: missing")
                continue
            data = path.read_bytes()
            if len(data) < 1000:
                errors.append(f"{path}: unexpectedly small ({len(data)} bytes)")
                continue
            if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
                errors.append(f"{path}: invalid PNG header")
                continue
            width, height = struct.unpack(">II", data[16:24])
            if (width, height) != expected_size:
                errors.append(f"{path}: expected {expected_size[0]}x{expected_size[1]}, got {width}x{height}")
            print(f"Validated {path}: {width}x{height}, {len(data)} bytes")
            count += 1
if errors:
    print("Screenshot validation failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)
print(f"Validated all {count} screenshots across phone/tablet, light/dark, and 100%/200% font scale.")
PY

echo "All 40 phone/tablet, light/dark, 100%/200% font-scale captures completed."
