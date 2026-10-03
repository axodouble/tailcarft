# ADR-008: Rebrand the mod from Tailcarft/mclink to Cauda

**Date:** 2026-10-03
**Status:** Accepted

## Context

The mod has carried a half-migrated identity since forking a BSD-3-Clause
Tailscale-origin project: the mod id is `tailcarft`, the display name is
Tailcarft, the Java package is `com.tailscale.mclink`, the asset namespace and
lang keys use `mclink`, the Maven group is `pe.jas.tailcarft`, and the bundled
helper binary is `mclink-helper`. The v0.3.0 "Neatify" milestone
(issue #27) calls for a proper name, assets, authorship, and final polish.
"Tailcarft" and "mclink" are no longer the intended product name, and the
mod's authorship should reflect its actual maintainer rather than the
upstream origin.

## Decision

Rename the product to **Cauda** (Latin for "tail", after the tunnel it makes)
and unify every user-facing and code identifier under it:

| Item | Before | After |
|------|--------|-------|
| Mod id | `tailcarft` | `cauda` |
| Display name | `Tailcarft` | `Cauda` |
| Java package | `com.tailscale.mclink` | `pe.jas.cauda` |
| Maven group | `pe.jas.tailcarft` | `pe.jas.cauda` |
| Asset namespace + lang keys | `mclink` / `mclink.*` | `cauda` / `cauda.*` |
| Config file | `mclink.json` | `cauda.json` |
| Mixin config files | `mclink.*.mixins.json` | `cauda.*.mixins.json` |
| Helper binary | `mclink-helper` | `cauda-helper` |
| Go module | `github.com/tailscale/mclink/helper` | `pe.jas.cauda/helper` |
| Class names | `TailcarftConfig`, `TailcarftMod`, `TailcarftServerEntry` | `CaudaConfig`, `CaudaMod`, `CaudaServerEntry` |
| Root Gradle project | `tailcat-for-minecraft` | `cauda-for-minecraft` |
| Mod author | `Tailscale`, `Jasper "Axodouble" V.` | `Jasper "Axodouble" V.` |
| Description | "Share Minecraft worlds through Tailcarft's userspace WireGuard transport." | "Share a singleplayer Minecraft world with friends over the internet — no dedicated server and no account. Cauda publishes your world the way vanilla Open to LAN does and tunnels the game port through a lightweight userspace WireGuard transport, so friends can join with a short-lived invite." |

**Deliberately kept (not part of the rebrand):**

- The external **Tailcat** transport (`github.com/tailscale/tailcat`) — a real
  third-party dependency the helper tunnels through. Renaming it would be
  wrong; it is credited in the helper's `go.mod`, not as the mod's author.
- The **invite wire format**: the `mcl1_` string prefix, the `tc…` token
  prefix, and the `{"version":1,"tailcat":…}` JSON field are protocol
  constants (see `Invites.java` / `helper/internal/invite/invite.go`). Changing
  them would break the invite handshake, so they are versioned as-is.
- **Historical records** under `docs/adr/`, `docs/fdr/`, `docs/superpowers/`,
  and `.superpowers/` keep their original names — they are a decision log that
  references what the project was called, not live product identity.

## Consequences

- The mod presents a single, consistent identity (Cauda) across all loaders and
  Minecraft versions, with its actual maintainer as the author.
- Old `mclink.json` configs and old `mcl1_`-era invites: the config file path
  changes to `cauda.json`, so existing user configs are left behind (acceptable
  at this pre-1.0 stage; the config only stores a couple of flags). Invites are
  ephemeral, so no migration is needed.
- The rename is mechanical but broad (package, entrypoints, mixin `package`
  fields, asset namespace, lang keys, helper module). It is verified by the
  full leaf build, the static mixin regression tests (ADR-002), and the
  headless GUI smoke test (ADR-004).
- External integrations (e.g. anything referencing the `pe.jas.tailcarft`
  Maven group or `com.tailscale.mclink` classes) are affected, but the project
  is pre-1.0 with no published downstream consumers.
