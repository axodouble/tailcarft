# Design: Persistent Tailcat server entry + dedicated-server hosting

Date: 2026-09-15
Status: approved in conversation (modpack use case confirmed, console-only invite)

## Purpose

Tailcat for Minecraft currently only supports ad-hoc sessions: a player hosting a
singleplayer world shares a temporary invite, and a joiner pastes it into a screen.
For a modpack that serves exactly one dedicated server, we want players to find a
ready-made entry at the top of the vanilla multiplayer list and join with one click —
no IP, no pasting. The dedicated server itself hosts the Tailcat tunnel
automatically and reuses the same invitation string across restarts, so the modpack
can ship a pre-filled client config.

## Scope

- Client: config-gated, pinned, non-deletable server entry in the 1.21.1
  multiplayer list; one-click join over the existing helper join path.
- Server: new `main` entrypoint that auto-starts a persistent Tailcat host when a
  dedicated server starts; invitation logged to console only.
- Helper (Go): `host --state-file` for stable key + DERP region across restarts.
  Rebuild of the five native helper binaries is required.

Out of scope: player-facing invite discovery (action bar / chat), multiple
configured servers, client-side commands, Realms.

## Client: configuration

File: `<gamedir>/config/mclink.json`

```json
{ "name": "Tailcat World", "tailcat": "mcl1_..." }
```

- `name` — display name of the list entry. Required, non-empty after trim;
  fallback default `Tailcat Server` if empty.
- `tailcat` — full `mcl1_...` invitation **or** a bare `tc...` token. A bare token
  is wrapped into the invitation envelope on the Java side:
  `mcl1_` + Base64url-no-padding of the exact JSON string
  `{"version":1,"tailcat":"<tc...>"}` (byte-identical to the Go encoder in
  `helper/internal/invite/invite.go`, including key order).
- No file, unreadable file, invalid JSON, or a `tailcat` value that matches neither
  `mcl1_` nor `tc` prefix → entry is not shown; no error surfaced (the mod is
  inert). The file is re-read on every `MultiplayerScreen` init, so edits to it
  apply on F5/reopen without a client restart.

Implementation: `TailcatConfig` (final class, static `load()` returning a
`record TailcatConfig(String name, String invite)` or `null`). JSON via Gson
(`FabricLoader` runtime, already a dependency through Fabric API). The
wrap/validation logic lives in a small static method `Invites` so it is unit
testable without a Minecraft client.

## Client: multiplayer list entry

Minecraft 1.21.1 (yarn `1.21.1+build.3-v2`) has no per-server info screen;
`MultiplayerScreen` shows inline Join/Direct/Add/Edit/Delete/Refresh/Back buttons
acting on the selected entry, and all join paths (Join button, Enter key,
double-click, join icon) funnel through the private
`MultiplayerScreen.connect(ServerInfo)`.

### Marker entry

A `ServerInfo` with:

- `name` — from config
- `address` — reserved marker string `mclink:tailcat` (never parsed: pinging only
  fires from `ServerInfo.Status.INITIAL`, and the marker is created pre-set)
- `ServerType.OTHER`
- pre-set: `setStatus(SUCCESSFUL)`, `ping = 1` (green 5-bar icon),
  `label = Text.translatable("mclink.server.label")`
  ("Connected via Tailcat tunnel"), `playerCountLabel = Text.empty()`,
  `playerListSummary` stays the default empty list

It renders exactly like an online vanilla server (default unknown-server icon, no
favicon). `WorldIcon.forServer` only hashes the address — verified safe.
`MultiplayerServerListPinger` is never invoked for it.

### Mixins (all client-side, added to `mclink.mixins.json`)

1. `MultiplayerScreenMixin`
   - `@Inject(init, at=TAIL)`: if config present and
     `getServerList().get("mclink:tailcat") == null` →
     `((ServerListAccessor) getServerList()).mclink$servers().add(0, marker)`,
     then `serverListWidget.setServers(getServerList())` to rebuild entries. Dedupe covers F5 refresh (fresh `ServerList` from `servers.dat`,
     which never contains the marker). The resize path of `init` leaves entries
     intact, so the marker survives window resizes.
   - `@Inject("connect(Lnet/minecraft/client/network/ServerInfo;)V", at=HEAD,
     cancellable)`: if `entry.address.equals("mclink:tailcat")` →
     `McLinkClient.state().join(client, screen, invite)` (existing seam: spawns
     helper, awaits ready, opens the vanilla `ConnectScreen` loading screen);
     on failure → return to the multiplayer screen and post the root error as a
     chat message via `ShareScreen.rootMessage`. Then `ci.cancel()`.
   - `@Inject(updateButtonActivationStates, at=TAIL)`: if the selected entry is a
     `ServerEntry` whose server address is the marker → force `buttonEdit.active =
     false; buttonDelete.active = false`. (Join stays enabled.)
2. `ServerListMixin` (`net.minecraft.client.option.ServerList`)
   - `@Inject(swapEntries, at=HEAD, cancellable)`: if either index is the marker's
     index → cancel. The entry cannot be moved with Shift+Arrow or the move icons.
   - `@Inject(saveFile, at=HEAD)` + `@Inject(saveFile, at=TAIL)`: remove the
     marker from the in-memory `servers` list before the write, restore it at
     index 0 after. The marker is never persisted to `servers.dat`, so deleting
     the config file makes the entry disappear with no stale entry.

`ServerList` has no public "insert at index 0" API (only append-style
`add(ServerInfo, boolean)`), so `ServerListMixin` additionally declares an
`@Accessor("servers")` for its private `List<ServerInfo> servers` field; the
init inject uses it to `add(0, marker)`. The save-file injects run inside the
`ServerList` class itself and touch `this.servers` directly. In
`MultiplayerScreen`, injected code runs inside that class, so
`serverListWidget`/`buttonEdit`/`buttonDelete`/`serverList` are reachable
directly, and the screen already exposes `getServerList()`.

## Client: join flow

One click on the entry (any of the join affordances) calls
`ScreenState.join(client, multiplayerScreen, invite)` — the same method the
paste-screen uses, already signature-ready for an invitation string. The vanilla
`ConnectScreen` loading screen provides the "connecting" feedback. `ScreenState`
and `JoinRemoteScreen` are otherwise unchanged; `JoinRemoteScreen` remains the
manual path behind the title-screen Connect button.

## Server: auto-hosting

- `fabric.mod.json`: `environment` becomes `"*"`; add
  `"main": ["com.tailscale.mclink.McLinkServer"]`.
- `McLinkServer implements DedicatedServerModInitializer`. That entrypoint fires
  for **every** server start in a merged environment, so guard with
  `if (!(server instanceof DedicatedServer ds)) return;` — integrated/LAN
  hosting keeps its existing Share-button flow.
- On start: spawn the helper
  `host --target 127.0.0.1:<ds.getPort()> --state-file <serverroot>/tailcat-for-minecraft/state.json`
  (`DedicatedServer.getPort()` is a public abstract method — no mixin needed).
  Read stdout events through the existing `HelperProcess`/`HelperEvent` types.
  On `ready` (mode host): log
  `[mclink] Tailcat invitation: <mcl1_...>` to the server console.
  On `error`: log a warning with the message and continue — a Tailcat outage
  must never take down the Minecraft server.
- On stop: `ServerLifecycleEvents.SERVER_STOPPING` → close the helper process.
  The server-side session state (helper process handle) lives in a small
  `ServerHost` class owned by `McLinkServer`; it is deliberately separate from
  the client-side `ScreenState`.

## Helper: `--state-file` (Go)

New flag on `host`: `--state-file <path>`.

- Unset → current behavior (fresh `key.NewNode()`, netcheck region, ephemeral).
- Set, file exists and parses:
  - state shape (JSON, no version key, additive-only evolution):
    `{"key": "<base64 node private key>", "region": <int>, "invite": "mcl1_..."}`
  - decode the key (`key.UnmarshalNodePrivate`), and if the saved region ID is
    still present in the DERP region map → `tailcat.ConnInfo{RegionID: saved}` +
    `Expand(ExpandForServer)` (deterministic map lookup, no netcheck) → same
    invitation forever.
  - if the saved region no longer exists → warn, fall back to
    `RegionID: -1` netcheck selection, rewrite the file with the new region.
  - recompute the invitation from the key (the stored `invite` is a cache, not
    the source of truth) and update it in the file if changed.
- Set, file missing or corrupt → behave like a fresh host: generate key, netcheck
  region, create the file (corrupt file: log a warning first).
- File is written `0600` (contains a private key), atomically (temp file +
  rename), in the same directory as `state.json`.

State directory: `<serverroot>/tailcat-for-minecraft/` (mod id as directory
name). Unversioned file name `state.json` so mod updates keep working state.

## Error handling summary

| Failure | Behavior |
|---|---|
| Client config missing/invalid | Entry hidden, mod inert, no errors |
| Helper join fails (client) | Back to multiplayer screen + chat message with root error |
| Helper host fails at server start (DERP unreachable, etc.) | Warning log; server runs normally without tailcat |
| Saved region gone from DERP map | Warn + reselect region + rewrite state; invitation string changes (operator re-shares) |
| State file corrupt | Warn + regenerate (old string becomes unusable) |
| Helper process dies mid-run (server) | Logged; no respawn (restarting the MC server re-establishes it) |

## Testing

- Java unit tests (JUnit 5, existing setup):
  - `Invites`: wrap a known `tc...` token → byte-exact expected `mcl1_...`
    string (fixture produced by the Go encoder); validation accepts both forms,
    rejects garbage.
  - `TailcatConfig.load`: valid, bare-token, missing file, invalid JSON, wrong
    types (table-driven, temp dirs).
- Go unit tests (`helper/`):
  - state round-trip (write/read, key decodes to the same public key, region
    preserved); corrupt file → error; missing file → create; 0600 perms.
  - region fallback decision logic (pure function over the region map).
- Manual E2E (documented in plan, run once): dedicated server starts → invite in
  console → client with config shows entry pinned at top with green status →
  one-click join works → restart server → identical invite → Shift+Arrow cannot
  move entry → Delete/Edit disabled → `servers.dat` contains no
  `mclink:tailcat` after any save.
- Native rebuild: `scripts/build-natives.sh`, then confirm the new binaries are
  bundled in the mod jar and `HelperEventTest`/`PlatformTest` still pass.

## Files touched

| File | Change |
|---|---|
| `helper/cmd/mclink-helper/main.go` | `--state-file` flag; state load/create in `runHost` |
| `helper/internal/state/` (new) | state file read/write, atomic 0600 |
| `mod/.../mclink/McLinkServer.java` (new) | server entrypoint, `ServerHost` lifecycle |
| `mod/.../mclink/TailcatConfig.java` (new) | config load + `Invites` wrap/validate |
| `mod/.../mclink/mixin/MultiplayerScreenMixin.java` (new) | 3 injects |
| `mod/.../mclink/mixin/ServerListMixin.java` (new) | swap guard, save filter |
| `mod/src/main/resources/mclink.mixins.json` | register new mixins |
| `mod/src/main/resources/fabric.mod.json` | `environment: "*"`, `main` entrypoint |
| `mod/src/main/resources/assets/mclink/lang/en_us.json` | `mclink.server.label` (+ error strings) |
| `mod/src/test/...` | new unit tests |

## Explicit non-goals

- No server command to print/rotate the invite (console log only; rotation =
  delete `state.json` and restart).
- No multiple configured servers; the config holds exactly one.
- No client-side display of the config path or errors beyond the chat message.
