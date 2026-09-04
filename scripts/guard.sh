#!/usr/bin/env bash
# Refuses to start heavy work when there is not enough memory for it.
#
# Exists because the alternative was discovered the hard way: a 3GB Gradle
# daemon, unbounded Kotlin daemons and a 3GB emulator on a 15GB laptop with 2GB
# of swap left no room to degrade, and the machine froze rather than slowing
# down. Every heavy step now states what it needs and is refused if it is not
# there.
#
# Usage:
#   scripts/guard.sh 3000 "gradle build"     # need 3000MB available
set -euo pipefail

need_mb="${1:?usage: guard.sh <needed-MB> <what>}"
what="${2:-work}"

available_mb=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
swap_free_mb=$(awk '/SwapFree/ {print int($2/1024)}' /proc/meminfo)

printf 'memory: %sMB available, %sMB swap free — %s needs %sMB\n' \
  "$available_mb" "$swap_free_mb" "$what" "$need_mb"

if [ "$available_mb" -lt "$need_mb" ]; then
  echo "REFUSED: not enough memory for $what" >&2
  echo "Close something, or stop the emulator with scripts/emulator.sh stop" >&2
  exit 1
fi

# Two JVM daemons plus an emulator is the combination that caused the freeze.
#
# Asked of adb rather than pgrep: `pgrep -f` matches whole command lines, so a
# shell whose own arguments merely mention the emulator matches itself. That
# false positive cost a confusing refusal the first time this ran.
emulator_running() {
  command -v adb >/dev/null 2>&1 || return 1
  adb devices 2>/dev/null | awk 'NR>1 && /^emulator-[0-9]+\s+device/ {found=1} END {exit !found}'
}

if [ "${ALLOW_EMULATOR_DURING_BUILD:-0}" != "1" ] && emulator_running; then
  case "$what" in
    *build*|*gradle*|*test*)
      echo "REFUSED: the emulator is running; do not build alongside it" >&2
      echo "Stop it first with scripts/emulator.sh stop" >&2
      exit 1
      ;;
  esac
fi

echo "ok"
