# FDR-001: Sharing a singleplayer world

**Status:** Active
**Last reviewed:** 2026-09-25

## Overview

Tailcarft lets the player of a singleplayer Minecraft world share that world
with others over the internet without a dedicated server. The host's game
publishes the world exactly the way vanilla *Open to LAN* does, and a bundled
helper process exposes the resulting local game port over
[Tailcat](https://github.com/tailscale/tailcat) (userspace WireGuard, no TUN
interface, no Tailscale account). Friends connect with an ephemeral
invitation. The mod is a client-side jar per Minecraft version and loader;
all networking lives in the helper, not in the client.

## Behavior

- **Host, explicitly:** pause a singleplayer game, click *Share with
  Tailcarft*, and copy the `mcl1_...` invitation. A server entry for the
  shared world appears in the multiplayer list for the host's own reference.
- **Host, via Open to LAN:** opening a world to LAN (vanilla menu) also
  becomes a Tailcarft share. Once the helper is ready, the invitation is
  printed in chat as a client system message (`Tailcarft invite: mcl1_...`)
  so the host can select and copy it, mirroring how vanilla announces that
  the world was opened to LAN.
- **Re-sharing:** if a host session already targets the LAN port, re-opening
  to LAN does not drop existing connections — the existing helper keeps
  running and the invite is re-announced. Opening to a different port
  restarts the helper on the new port.
- **Stop:** sharing ends when the host stops sharing, closes the LAN
  session, or leaves the world; the helper process is terminated.
- **Join:** from the multiplayer screen, *Connect with Tailcarft* starts a
  join session and connects once ready, or a pasted invitation in any server
  address field is recognized and joined the same way.
- **Failure:** if the helper fails to start or reports an error, the invite
  is not announced and the error is surfaced (log and, where a screen is
  involved, the UI) instead of silently doing nothing.
- **Exposure:** only the game port is exposed to a holder of the invitation;
  the invitation is ephemeral and valid only while sharing is on.

## Design Decisions

### 1. Reuse vanilla Open to LAN as the hosting primitive

**Decision:** The mod does not implement its own server bootstrap. Hosting
publishes the world through the game's own LAN path and the helper binds to
the port that path chose.
**Why:** The LAN path is the game's supported way to turn a singleplayer
world into a reachable server; reusing it keeps behavior consistent with
what the player already knows and avoids maintaining a parallel server
lifecycle. The explicit *Share with Tailcarft* action and the passive
*Open to LAN* hook both funnel into the same host session.
**Tradeoff:** The mod inherits whatever port the game picks and must react to
the game's publish/unpublish events rather than driving them, which is what
makes the mixin hooks (decision 3) load-bearing.

### 2. The helper process owns all networking

**Decision:** A bundled Go helper, started as a child process, owns the
Tailcat connection and the game-port forwarding; the client only starts it,
reads its events, and tears it down.
**Why:** Keeping WireGuard/userspace transport out of the JVM isolates the
networking failure domain, lets the helper be built and versioned
independently, and means a networking fault cannot take down the game.
**Tradeoff:** The client must babysit a subprocess (readiness timeout, error
events, termination on world exit), and there is a process boundary to keep
in sync across platforms.

### 3. React to the game through exit-point mixin hooks, using RETURN not TAIL

**Decision:** Whether a world is published or unpublished to LAN is detected
by mixin hooks on the integrated server's publish/unpublish methods, and the
publish hook is injected at `@At("RETURN")` and filters on the boolean return
value rather than at `@At("TAIL")`.
**Why:** TAIL resolves to the last return instruction in bytecode order,
which in these methods is an exception-handler or guard-clause return, so a
TAIL hook fired only on the failure/skip path and the invite was never
announced on a successful publish — with no build or runtime error to show
why. RETURN fires at every exit and the callback checks the return value, so
the hook fires on the success path it actually cares about. See ADR-001; the
choice is enforced at build time by ADR-002's regression test.
**Tradeoff:** The callback runs at every return site and must filter on the
return value, and the exact per-version method descriptors must be kept
correct.

### 4. Announce the invite in chat, not only in a GUI button

**Decision:** On a successful LAN publish the invitation is printed as a chat
system message, in addition to the explicit share UI.
**Why:** The *Open to LAN* flow has no Tailcarft UI of its own, so chat is
the only always-available surface; a copyable chat line matches the way
vanilla itself reports the LAN port and needs no extra screen.
**Tradeoff:** Chat messages can scroll away, so the explicit share button
remains the primary, discoverable host path.

### 5. Ephemeral, port-scoped invitations

**Decision:** An invitation is valid only while the host session is alive and
only the game port is exposed to its holder.
**Why:** This bounds the blast radius of a leaked invitation to the duration
of an active share and to the game itself, which is the appropriate trust
model for a feature a player turns on and off casually.
**Tradeoff:** Reconnects after a share is stopped require a fresh invitation.

### 6. The supported loader/version matrix follows upstream availability

**Decision:** A jar is built for a (loader, Minecraft version) combination
only when that loader publishes for that version. At the time of writing the
matrix is Fabric (1.20.1, 1.21.1, 26.3), NeoForge (1.21.1, 26.3), Forge
(1.20.1, 1.21.1, 26.3), and Quilt (1.20.1, 1.21.1); LiteLoader and Quilt for
26.3 are unsupported because no loader exists for them. See ADR-003.
**Why:** Shipping a jar for a loader/version pair with no upstream loader
would mean bundling an uncertified loader, which is a support burden without
a user benefit.
**Tradeoff:** Users on a loader that has not kept up with a Minecraft line
must switch loaders or wait.

## Related

- **ADRs:** ADR-001, ADR-002, ADR-003
- **FDRs:** —
