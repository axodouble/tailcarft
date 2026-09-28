#!/usr/bin/env bash
# Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
#
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#
# Headless GUI smoke test for one loader leaf. Boots the client OFFLINE under
# Xvfb + Mesa software GL (no GPU, no Mojang auth), opens the Multiplayer
# screen via the TMC_GUI_SMOKE hook in the leaf's ClientMod, verifies the
# "join with tailcarft" (mclink.join) button is present, and captures the real
# X11 window (ImageMagick `import`) for visual confirmation.
#
# Why `import` and not the in-game Screenshot API: under software rendering the
# in-game GL framebuffer read is stuck at the startup splash even after the
# game has moved on to a normal screen. So the hook keeps the client alive on
# the Multiplayer screen after it prints its verdict, and this script grabs the
# Xvfb display directly.
#
# Requires: Xvfb, Mesa, ImageMagick (`import`).
#
# Exit codes:
#   0  button present (pass)
#   1  button absent  (fail - the GUI regression under test)
#   2  could not determine (timeout, crash, or infra/compile failure)
#
# Usage:
#   scripts/gui-smoke.sh [leaf] [timeout-seconds]
#   leaf        defaults to fabric-1.20.1. Works for any leaf that exposes a
#               runClient task - all three 26.3 loaders do (fabric-26.3,
#               forge-26.3, neoforge-26.3), plus the older Fabric/Quilt leaves.
#               The NeoForge leaf needs its installDevMod task to have placed
#               the built JAR in run/mods/ (runClient depends on it), because
#               the classpath mod discoverer skips source-set output dirs.
#   timeout     defaults to 300 (software rendering is slow to boot)
#   TMC_DISPLAY (env)  X11 display to use, defaults to :97
#
# See docs/adr/ADR-004-headless-gui-smoke-test.md for the full rationale and
# the per-version API notes.

set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
LEAF=${1:-fabric-1.20.1}
TIMEOUT=${2:-300}
DISP=${TMC_DISPLAY:-:97}
cd "$ROOT"

command -v Xvfb >/dev/null 2>&1 || { echo "ERROR: Xvfb not on PATH" >&2; exit 2; }
[ -d "$ROOT/$LEAF" ] || { echo "ERROR: unknown leaf '$LEAF' (no directory)" >&2; exit 2; }

LOG="/tmp/gui-smoke-$LEAF.log"

# --- software GL (no GPU on the test host) ---
export __GLX_VENDOR_LIBRARY_NAME=mesa
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
# Mesa-only EGL vendor dir: the NVIDIA EGL driver segfaults headless, and
# __EGL_VENDOR_LIBRARY_FILE is ignored by libEGL - only the DIRS override works.
mkdir -p /tmp/egl-mesa
[ -e /tmp/egl-mesa/mesa.json ] || ln -sf /usr/share/glvnd/egl_vendor.d/50_mesa.json /tmp/egl-mesa/mesa.json
export __EGL_VENDOR_LIBRARY_DIRS=/tmp/egl-mesa
# No audio device in CI: silence OpenAL/ALSA via a null PCM so the missing
# device is not logged as an error.
mkdir -p /tmp/alsa-null
printf 'pcm.!default { type null }\nctl.!default { type null }\n' > /tmp/alsa-null/asound.conf
export ALSA_CONFIG_PATH=/tmp/alsa-null/asound.conf

# --- the smoke hook (inert unless set) ---
export TMC_GUI_SMOKE=1
export DISPLAY="$DISP"

cleanup() {
  kill "$XVFB" 2>/dev/null
  kill "$GW" 2>/dev/null
  # Kill any orphaned game/build JVMs. Safe here: this file's command line is
  # "bash scripts/gui-smoke.sh <leaf>", so none of these patterns self-match.
  pkill -9 -f 'KnotClient' 2>/dev/null
  pkill -9 -f 'net.neoforged' 2>/dev/null
  pkill -9 -f 'net.minecraftforge' 2>/dev/null
  pkill -9 -f 'GradleWrapperMain' 2>/dev/null
}
trap cleanup EXIT

# Free a stale Xvfb on the display (by PID, never by a self-matching pattern).
STALE=$(ps -eo pid,args | grep -F "Xvfb $DISP" | grep -v grep | awk '{print $1}')
[ -n "${STALE:-}" ] && { kill -9 $STALE 2>/dev/null; sleep 1; }

Xvfb "$DISP" -screen 0 1280x720x24 +extension GLX +extension RANDR +extension RENDER -nolisten tcp -ac >/tmp/xvfb-smoke.log 2>&1 &
XVFB=$!
sleep 2
if ! kill -0 "$XVFB" 2>/dev/null; then
  echo "ERROR: Xvfb failed to start on $DISP (see /tmp/xvfb-smoke.log)" >&2
  exit 2
fi

./gradlew ":$LEAF:runClient" --no-daemon > "$LOG" 2>&1 &
GW=$!

# Poll until the verdict line appears, gradlew exits, or the timeout elapses.
DEADLINE=$(( $(date +%s) + TIMEOUT ))
VERDICT=""
while :; do
  VERDICT=$(grep -a 'TMC_GUI_SMOKE joinButton' "$LOG" 2>/dev/null | tail -1)
  [ -n "$VERDICT" ] && break
  kill -0 "$GW" 2>/dev/null || break
  [ "$(date +%s)" -ge "$DEADLINE" ] && break
  sleep 3
done

if kill -0 "$GW" 2>/dev/null && [ -z "$VERDICT" ]; then
  echo "WARN: timeout after ${TIMEOUT}s; killing client" >&2
fi

echo "--- smoke verdict ---"
echo "${VERDICT:-<none>}"

# Grab the real X11 window from Xvfb while the client is still alive on the
# Multiplayer screen (the hook stays up after printing its verdict for this).
# The in-game GL framebuffer is stuck at the startup splash under software
# rendering, so the screenshot is taken of the display, not in-game.
SHOT="$LEAF/run/screenshots/mclink_smoke.png"
mkdir -p "$LEAF/run/screenshots"
if kill -0 "$GW" 2>/dev/null && command -v import >/dev/null 2>&1; then
  sleep 2   # let a clean frame present
  DISPLAY="$DISP" import -window root "$SHOT" 2>/dev/null
fi
echo "--- screenshot ---"
if [ -f "$SHOT" ]; then
  echo "$SHOT ($(file -b "$SHOT" | cut -d, -f1))"
else
  echo "<none captured (client not alive, or ImageMagick import missing)>"
fi
echo "--- log: $LOG ---"

case "$VERDICT" in
  *joinButton=true*)  echo "RESULT: PASS (mclink.join button present)"; exit 0 ;;
  *joinButton=false*) echo "RESULT: FAIL (mclink.join button ABSENT)";  exit 1 ;;
  *)                  echo "RESULT: UNKNOWN (no verdict; see log)";      exit 2 ;;
esac
