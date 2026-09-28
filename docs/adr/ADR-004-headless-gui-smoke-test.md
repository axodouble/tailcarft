# ADR-004: Headless GUI smoke test

**Date:** 2026-09-27
**Status:** Accepted

## Context

Tailcarft's client mod adds a "Connect with Tailcarft" button to the
multiplayer server-list screen. A change that affects that screen (the 26.3
loader was one) can silently stop rendering the button, and nothing in the
build or unit-test pipeline would catch it: the leaf jars compile and their
ASM-based static mixin checks (ADR-002) pass even when the button is gone at
runtime. We need a repeatable, committed way to confirm the button actually
appears in a real client before pushing GUI-affecting changes, so future
agents can run it without a display.

Two things make this non-trivial under a headless runner:

- There is no real GPU. The game's OpenGL context must fall back to software
  rendering (Mesa `llvmpipe`) and the window must live on a virtual display.
- Newer versions (1.21.1, 26.3) show a first-launch
  `AccessibilityOnboardingScreen` that blocks a direct `setScreen`, and the
  multiplayer path is no longer a single hop: it is title screen →
  `SafetyScreen` (a "Proceed" button) → `JoinMultiplayerScreen`. An older
  version (1.20.1) still has a single hop.

## Decision

A per-version `ClientMod` smoke hook, gated on the `TMC_GUI_SMOKE`
environment variable so it is inert in normal play, drives the client to the
`JoinMultiplayerScreen` and reports whether the mod's `mclink.join` button is
in the widget tree. A committed `scripts/gui-smoke.sh` boots the client
headlessly, captures the real X11 window, and turns the verdict into an exit
code.

- `scripts/gui-smoke.sh <leaf>` starts `Xvfb` (1280x720x24, GLX enabled),
  points the client at Mesa software GL (`__GLX_VENDOR_LIBRARY_NAME=mesa`,
  `LIBGL_ALWAYS_SOFTWARE=1`, `GALLIUM_DRIVER=llvmpipe`, a null EGL vendor dir
  and a null `ALSA_CONFIG_PATH`), runs the leaf's `runClient` with
  `TMC_GUI_SMOKE=1`, waits for the `TMC_GUI_SMOKE` verdict line, then captures
  the window with ImageMagick `import -window root` (an in-game screenshot is
  unreliable here — the framebuffer stays on the splash under llvmpipe) and
  exits `0` (button present), `1` (absent), or `2` (no verdict / client died).

- The per-version hook walks the game's own navigation rather than forcing a
  screen, because forcing is blocked on newer versions:
  - 1.20.1 waits for the game to settle on a stable screen, then calls
    `setScreen(new JoinMultiplayerScreen(...))` directly (single hop).
  - 1.21.1 and 26.3 click the title screen's "Multiplayer" button, then the
    `SafetyScreen`'s "Proceed" button, to reach the `JoinMultiplayerScreen`
    the way a player would.

- On 1.21.1 and 26.3 an `AccessibilityOnboardingScreenMixin` dismisses the
  first-launch onboarding screen (redirecting it to the `TitleScreen`) when
  `isGuiSmokeTest()` is true, so the title screen — and its "Multiplayer"
  button — is reachable. Without it the hook stalls on the onboarding screen.

- The hook tolerates the loader/version API drift that already exists:
  1.20.1 and 1.21.1 read the current screen from `client.screen` and open
  screens via `client.setScreen(...)`, while 26.3 uses `client.gui.screen()`
  and `client.gui.setScreen(...)`. Likewise 1.20.1/1.21.1 call
  `Button.onPress()` with no arguments and 26.3 calls
  `Button.onPress(InputWithModifiers)`.

## Consequences

- Any agent can run `bash scripts/gui-smoke.sh <leaf>` to verify the Connect
  button renders before pushing a GUI-affecting change; the captured PNG in
  `<leaf>/run/screenshots/mclink_smoke.png` is a human-checkable artifact.
- The hook and the onboarding Mixin are inert in production: both act only
  while `TMC_GUI_SMOKE` is set, so the extra Mixin and the per-tick check add
  nothing to a normal game.
- The host needs `Xvfb`, ImageMagick, and Mesa (llvmpipe) for the GL path;
  the script reports `exit 2` rather than a false pass when the client never
  reaches a verdict.
- The script drives the leaf's `runClient` task. All three 26.3 loaders
  (Fabric, Forge, NeoForge) declare a client run task, so the tool reaches each
  of them directly: `TMC_DISPLAY=:104 ./scripts/gui-smoke.sh <leaf> 420`. The
  NeoForge leaf additionally requires its `installDevMod` task to have placed
  the built JAR in `run/mods/` (the classpath-based mod discoverer skips
  source-set output dirs), which `runClient` depends on.
- As of this ADR the button renders in all three 26.3 loaders. The 26.3
  regression that motivated the tool was real **on Forge 26.3 only** and is
  explained by ADR-005 (the Forge/NeoForge Mixin pipeline does not weave the
  late `Screen`/`JoinMultiplayerScreen` targets, so the button is added from
  the `Minecraft` tick hook instead). The tool now catches a recurrence in any
  loader.
- A version that changes its multiplayer navigation (new intermediate screen,
  renamed button, new onboarding gate) will break the hook for that version
  only; the fix is local to that version's `ClientMod` and Mixin.
