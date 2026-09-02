#!/usr/bin/env bash
# Toolchain paths for building Dewey. Source it: `. scripts/env.sh`
#
# Both the JDK and the Android SDK live under $HOME rather than being installed
# system-wide, so a build needs no root and touches nothing outside your home
# directory. Override either variable if you already have them elsewhere.

export JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk/jdk-17.0.20.1+1}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$PATH"

for required in "$JAVA_HOME/bin/java" "$ANDROID_HOME/platform-tools/adb"; do
  [ -x "$required" ] || echo "warning: missing $required — see README build section" >&2
done
