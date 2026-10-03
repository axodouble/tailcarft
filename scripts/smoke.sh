#!/usr/bin/env bash
# Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
#
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#
# Headless smoke-test dispatcher for the cauda loaders. Boots the client
# OFFLINE under Xvfb + Mesa software GL (no GPU, no Mojang auth) and drives a
# single env-gated hook in the leaf's ClientMod, then greps the verdict line
# the hook prints.
#
# Subcommands:
#   gui     verify the "join with cauda" (cauda.join) button is present on
#           the Multiplayer screen (delegates to gui-smoke.sh).
#   server  seed a server-config invite (run/config/cauda.json), open the
#           Multiplayer screen, and verify the marker server entry is present
#           AND a click on it is re-routed to the Cauda join flow
#           (JoinRemoteScreen). This is the server-config feature (ADR-005): on
#           ModLauncher loaders the late-loaded MultiplayerScreenMixin is not
#           woven, so the entry + click-to-join ride the per-tick hook.
#
# Usage:
#   scripts/smoke.sh <subcommand> [leaf] [timeout-seconds]
#   leaf        defaults to fabric-26.3 (server) / fabric-1.20.1 (gui). Any leaf
#               with a runClient task works.
#   timeout     defaults to 300 (software rendering is slow to boot)
#
# Env:
#   TMC_DISPLAY       X11 display to use (default :97)
#   TMC_SMOKE_INVITE  cauda.json "tailcat" invite to seed (server subcommand
#                     only). Defaults to a structurally-valid smoke invite; the
#                     re-route is verified before the (fake) DERP connect fails.
#
# Exit codes: 0 pass, 1 fail, 2 unknown/infra.
#
# See docs/adr/ADR-004-headless-gui-smoke-test.md and
# docs/adr/ADR-005-forge-mixin-skips-late-screen-classes.md for rationale.

set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SUB=${1:-}
case "$SUB" in
  gui)
    shift
    exec "$ROOT/scripts/gui-smoke.sh" "$@"
    ;;
  server)
    shift
    LEAF=${1:-fabric-26.3}
    TIMEOUT=${2:-300}
    DISP=${TMC_DISPLAY:-:97}
    cd "$ROOT"

    command -v Xvfb >/dev/null 2>&1 || { echo "ERROR: Xvfb not on PATH" >&2; exit 2; }
    [ -d "$ROOT/$LEAF" ] || { echo "ERROR: unknown leaf '$LEAF' (no directory)" >&2; exit 2; }

    INVITE=${TMC_SMOKE_INVITE:-"mcl1_eyJ2ZXJzaW9uIjoxLCJ0YWlsY2F0IjoidGNzbW9rZXRlc3QwMSJ9"}
    CONFIG="$ROOT/$LEAF/run/config/cauda.json"
    mkdir -p "$(dirname "$CONFIG")"
    printf '{"name":"Cauda Smoke","tailcat":"%s"}\n' "$INVITE" > "$CONFIG"

    LOG="/tmp/server-smoke-$LEAF.log"

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
    export TMC_SERVER_SMOKE=1
    export DISPLAY="$DISP"

    cleanup() {
      kill "$XVFB" 2>/dev/null
      kill "$GW" 2>/dev/null
      rm -f "$CONFIG"
      # Kill any orphaned game/build JVMs. Safe here: this file's command line is
      # "bash scripts/smoke.sh server <leaf>", so none of these patterns self-match.
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
      VERDICT=$(grep -a 'TMC_SERVER_SMOKE entry=' "$LOG" 2>/dev/null | tail -1)
      [ -n "$VERDICT" ] && break
      kill -0 "$GW" 2>/dev/null || break
      [ "$(date +%s)" -ge "$DEADLINE" ] && break
      sleep 3
    done

    if kill -0 "$GW" 2>/dev/null && [ -z "$VERDICT" ]; then
      echo "WARN: timeout after ${TIMEOUT}s; killing client" >&2
    fi

    echo "--- server-config verdict ---"
    echo "${VERDICT:-<none>}"
    echo "--- log: $LOG ---"

    if [ -z "$VERDICT" ]; then
      echo "RESULT: UNKNOWN (no verdict; see log)"
      exit 2
    fi
    case "$VERDICT" in
      *entry=true*join=true*) echo "RESULT: PASS (marker entry present, join re-routed)"; exit 0 ;;
      *)                      echo "RESULT: FAIL (verdict not a full pass; see log)";    exit 1 ;;
    esac
    ;;
  *)
    echo "usage: $0 {gui|server} [leaf] [timeout-seconds]" >&2
    exit 2
    ;;
esac
