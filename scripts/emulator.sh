#!/usr/bin/env bash
# Starts and stops the emulator with a memory ceiling, and never silently.
#
#   scripts/emulator.sh start
#   scripts/emulator.sh stop
#   scripts/emulator.sh status
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
. "$here/env.sh"

AVD="${AVD:-dewey}"
# 2GB rather than 3: enough for the app, the 118MB encoder and OCR, while
# leaving the host room for a build afterwards.
MEMORY_MB="${MEMORY_MB:-2048}"

case "${1:-status}" in
  start)
    "$here/guard.sh" 4000 "the emulator"
    if adb devices 2>/dev/null | grep -qE '^emulator-[0-9]+\s+device'; then
      echo "already running"; exit 0
    fi
    nohup emulator -avd "$AVD" \
      -no-snapshot-save -no-boot-anim -no-audio \
      -gpu swiftshader_indirect \
      -memory "$MEMORY_MB" \
      > /tmp/dewey-emulator.log 2>&1 &
    echo "starting $AVD with ${MEMORY_MB}MB; log at /tmp/dewey-emulator.log"
    ;;
  stop)
    adb emu kill 2>/dev/null || true
    sleep 3
    # Only the actual binaries, matched on process name rather than command line.
    pkill -x qemu-system-x86_64 2>/dev/null || true
    pkill -x qemu-system-aarch64 2>/dev/null || true
    echo "stopped"
    ;;
  status)
    if adb devices 2>/dev/null | grep -qE '^emulator-[0-9]+\s+device'; then
      echo "running"; adb devices | tail -n +2
    else
      echo "not running"
    fi
    ;;
  *)
    echo "usage: $0 {start|stop|status}" >&2; exit 2
    ;;
esac
