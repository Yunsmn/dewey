#!/usr/bin/env bash
# Starts and stops the emulator with a memory ceiling, and never silently.
#
#   scripts/emulator.sh start
#   scripts/emulator.sh stop
#   scripts/emulator.sh status
#   scripts/emulator.sh slim     disable Google apps Dewey never touches
#   scripts/emulator.sh unslim   put them back
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
  slim|unslim)
    # The AVD uses a google_apis_playstore image, which boots Gmail, Maps,
    # Photos, YouTube, the Google app and a dozen other things that between
    # them hold ~600MB the app could be using. Disabling per user rather than
    # uninstalling, so `unslim` puts everything back and no system image has to
    # be re-downloaded.
    #
    # An explicit list, never a pattern. Four of these are load-bearing and are
    # named here so nobody adds them by hand later:
    #
    #   com.google.android.documentsui        the SAF folder picker
    #   com.google.android.permissioncontroller  the runtime permission dialogs
    #   com.google.android.inputmethod.latin  the keyboard, for the search box
    #   com.google.android.apps.docs          opens a PDF when a row is tapped
    #
    # com.android.vending stays too: ML Kit's document scanner is a Play
    # services module fetched on demand, and that goes through the Store.
    slim_packages=(
      com.google.android.googlequicksearchbox
      com.google.android.apps.messaging
      com.google.android.apps.maps
      com.google.android.apps.photos
      com.google.android.youtube
      com.google.android.apps.youtube.music
      com.google.android.gm
      com.google.android.calendar
      com.google.android.contacts
      com.google.android.dialer
      com.google.android.deskclock
      com.google.android.tts
      com.google.android.apps.wellbeing
      com.google.android.apps.safetyhub
      com.google.android.as
      com.google.android.as.oss
      com.google.android.marvin.talkback
      com.google.android.accessibility.switchaccess
      com.google.android.apps.accessibility.voiceaccess
      com.google.android.projection.gearhead
      com.google.android.apps.restore
      com.google.android.settings.intelligence
      com.google.android.apps.customization.pixel
      com.google.android.avatarpicker
    )

    adb devices | grep -qE '^emulator-[0-9]+\s+device' || { echo "no emulator running" >&2; exit 1; }

    if [ "$1" = "slim" ]; then verb="disable-user --user 0"; else verb="enable"; fi

    for pkg in "${slim_packages[@]}"; do
      # A package absent from this image is not an error worth stopping for.
      out="$(adb shell pm $verb "$pkg" 2>&1 | tr -d '\r')"
      case "$out" in
        *"new state"*) echo "$pkg: ok" ;;
        *) echo "$pkg: $out" ;;
      esac
    done
    ;;
  *)
    echo "usage: $0 {start|stop|status|slim|unslim}" >&2; exit 2
    ;;
esac
