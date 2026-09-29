# ADR-005: Forge/NeoForge do not weave Mixins into late GUI-screen classes — drive screen UI from an early, always-woven class

**Date:** 2026-09-28
**Status:** Accepted

## Context

The 26.3 client adds a "Connect with Tailcarft" button to the multiplayer
`JoinMultiplayerScreen`. The natural implementation targets the screen classes
directly with Mixins (an accessor on `Screen`, and `@Inject` hooks on
`JoinMultiplayerScreen`). On Fabric 26.3 that worked. On Forge 26.3 the button
was missing, even though the jars compiled and the ASM static mixin checks
(ADR-002) passed.

The deeper question this ADR answers: **why does a `@Mixin(Minecraft.class)`
get woven on Forge, but a `@Mixin(Screen.class)` / `@Mixin(JoinMultiplayerScreen.class)`
does not, in the same mod, same classloader, same Mixin environment?**

The practical consequence is that any GUI behaviour we want on a
ModLauncher-based loader (Forge, NeoForge) cannot be assumed to be delivered by
a Mixin that targets a screen class.

## Root cause

The two loader families apply Mixin through different mechanisms:

- **Fabric (and Quilt)** apply Mixin through a **Java agent**
  (`-javaagent`). The agent registers a `ClassFileTransformer` via
  `Instrumentation`, which fires for **every** `defineClass` in the JVM, no
  matter when the class loads. So a Mixin targeting a screen is woven the first
  time that screen class is defined — always.
- **Forge / NeoForge** (ModLauncher) apply Mixin through a **ModLauncher
  `ITransformer`** that runs inside the `TransformingClassLoader`'s transform
  pipeline. The Mixin `ITransformer` (`org.spongepowered.asm.mixin.transformer.MixinTransformer`)
  simply delegates to `MixinProcessor.applyMixins` with no phase gate of its
  own; whether a given class actually gets woven depends on that class going
  through the `TransformingClassLoader` transform path while the Mixin
  environment is in a state where it will accept the target.

Empirically (26.3, Mixin 0.8.7), on Forge:

1. The Mixin environment is already in the **`DEFAULT` phase** when the config
   is prepared. The verbose log reads
   `Preparing mixins for MixinEnvironment[DEFAULT]`. The phase stays `DEFAULT`
   through mod-init, the first tick, and the moment the multiplayer screen is
   shown, and the `MixinEnvironment` object identity is stable the whole time
   (`identityHashCode` unchanged), so `MixinConfig.select(env)` (a reference
   equality check) is not what's disqualifying the config.
2. The `Minecraft` Mixin **is** woven (the per-tick hook fires). The
   `Screen` and `JoinMultiplayerScreen` Mixins **are not**: the accessor
   interface is absent from `Screen`, and the `JoinMultiplayerScreen`
   `@Inject` hooks never fire.
3. All three classes are loaded by the **same** `TransformingClassLoader[TRANSFORMER]`
   instance, so classloader identity is not the differentiator either.
4. **Decisive:** force-loading `Screen` through that classloader from
   mod-init (i.e. after `Preparing mixins`, with the environment live and in
   `DEFAULT`) does **not** result in the `ScreenAccessorMixin` being woven.
   Merely loading the class "later" does not cause the weave to happen.

The net behaviour, independent of the exact byte-level trigger inside
ModLauncher: **on a ModLauncher loader, the early main-class target
(`Minecraft`) gets its Mixin, but the late GUI-screen targets (`Screen`,
`JoinMultiplayerScreen`) do not.** Fabric's agent-based application has no such
load-timing sensitivity, which is why the identical Mod works there.

## Decision

For GUI behaviour that must render on every supported loader, do not rely on a
Mixin that targets a late GUI-screen class to add the widget. Instead target an
early, always-woven class and drive the screen UI from there:

- A `@Mixin(Minecraft.class)` injects once per tick into `ClientMod.onTick`.
- `onTick` checks whether the current screen is the target
  (`JoinMultiplayerScreen`) and, on first appearance / after a resize, adds the
  "Connect with Tailcarft" button. The widget add uses the `ScreenAccessorMixin`
  accessor when that Mixin happens to be applied, and falls back to reflection
  over the protected `Screen` widget methods when it is not (the Forge/NeoForge
  case).

The screen-targeting Mixins are kept for the loaders where they are woven
(Fabric/Quilt): `MultiplayerScreenMixin` adds the marker server-list entry and
intercepts the click on it, and `ConnectScreenMixin` intercepts a directly typed
invite address. On a ModLauncher loader (Forge/NeoForge) those are not woven,
so the tick hook carries that behaviour too — the marker entry and its
click-to-join re-route, documented in the "third consequence" below.

**Why:** It is the smallest change that makes the button render identically on
Fabric, Forge, and NeoForge, and it degrades gracefully (accessor-when-present,
reflection-when-not) rather than crashing on a loader whose Mixin is absent.

**Tradeoff:** The widget is added from a per-tick poll guarded by screen
instance + size (so it does not re-add every tick or stack after a resize), and
the protected-method reflection path must track the `Screen` widget API per
version.

## Second consequence: the LAN-hosting trigger is also a late-class Mixin

The same root cause breaks a second, non-GUI behaviour. "Open to LAN" in a
singleplayer world is detected by a `@Mixin(IntegratedServer.class)` that hooks
`publishServer(scope, port)` and calls `ScreenState.onPublished(server, port)`,
which starts the Tailcarft host on the published LAN port and posts the invite
in chat. `IntegratedServer` is loaded only when a singleplayer world is entered —
long after Mixin preparation — so on a ModLauncher loader (Forge, NeoForge) that
Mixin is **not woven**, `onPublished` never fires, and a plain "Open to LAN" world
is never shared on those loaders. (Fabric/Quilt weave it fine, because the agent
applies the Mixin whenever the class is defined.)

The only other hosting path — `ServerStartedEvent → ServerMod.onStarted →
ServerHost.start(server)` — targets `127.0.0.1:` + `server.getPort()`. For an
integrated server `getPort()` returns the `publishedPort` field (initialised `-1`,
set only when LAN is opened), so for a world that is not yet on LAN it targets
port `-1` and the helper rejects it; this path is a harmless dead end for
singleplayer and is not what shares the LAN port.

### Fix

Drive the hosting trigger from the always-woven per-tick hook, exactly as the
button is driven. `ScreenState` gains a `syncLanHosting(client)` method, called at
the top of `tick()`: it reads `client.getSingleplayerServer().getPort()` and,
**only on a transition** (`-1 → port` or `port → -1`, tracked in `lastLanPort`),
calls `onPublished` / `onUnpublished`. `onPublished` is de-duped for the same
live port, so on a loader where the `IntegratedServer` Mixin *is* woven, the mixin
and the tick hook firing for the same port yield exactly one host session and one
chat invite, not two.

The `IntegratedServer.getPort() → publishedPort` contract is confirmed for all
three supported versions (26.3, 1.21.1, 1.20.1) — it is the same field the mixin
receives as its `port` argument — so the fix is applied to all three `ScreenState`
classes. On the Mixin-less loaders it is the sole hosting trigger; on the
Mixin-woven loaders it is a de-duped no-op once the mixin has fired.

## Third consequence: the marker server-list entry and its click-to-join are also late-class Mixins

The same root cause breaks the server-config feature on the same loaders. A
server config (`run/config/mclink.json`) is surfaced on the multiplayer screen
as a marker entry (`TailcarftServerEntry`, a `ServerData` whose `ip` is a
sentinel) that, when clicked, opens the Tailcarft join flow instead of dialing
the sentinel. That behaviour — adding the entry to the list, intercepting the
click on it, and disabling edit/delete while it is selected — was delivered
entirely by `MultiplayerScreenMixin` (`@Mixin(JoinMultiplayerScreen.class)`)
and `ConnectScreenMixin` (`@Mixin(ConnectScreen.class)`). On a ModLauncher
loader (Forge, NeoForge) **neither is woven**: `JoinMultiplayerScreen` is a late
GUI-screen class, and `ConnectScreen` is created even later (only when a join is
attempted). So on those loaders the marker entry never appeared and click-to-join
never fired; only the tick-hook "Connect with Tailcarft" button worked, and the
config-driven entry was silently missing.

### Fix

Deliver the marker entry and its click-to-join from the always-woven per-tick
hook, mirroring the button and hosting fixes. Two changes:

1. **The sentinel is made to hang.** It was changed from a non-dialable
   placeholder (`mclink:tailcarft`, which failed instantly at `getPort()` — the
   connector thread threw before any `ConnectScreen` was shown, leaving the hook
   no window to act) to a **hanging** address — an RFC 5737 TEST-NET-1 host
   (`192.0.2.1`). When the marker is joined, the vanilla `ConnectScreen` opens
   and hangs on the TCP connect instead of failing. That hang is the window.

2. **The tick hook owns the entry and the re-route.** On each tick, while the
   current screen is the multiplayer screen, `ClientMod.ensureMarkerEntry` adds
   the marker entry reflectively (if absent), tracks whether it is the selected
   entry, and disables edit/delete while it is. When the selected entry is the
   marker and the screen next becomes a `ConnectScreen`, the hook sets that
   screen's `aborted` flag and re-routes to the same `JoinRemoteScreen` the Mixin
   produces on Fabric.

On Fabric/Quilt the Mixin intercepts the join at `join(ServerData)` HEAD and
cancels it, so the sentinel is never dialed and the tick-hook re-route is inert
(the entry is already present from the Mixin, so the reflective add is a no-op).
On Forge/NeoForge the Mixin is absent, so the tick-hook is the sole path. Both
converge on `JoinRemoteScreen` with the config's invite. Verified by
`scripts/smoke.sh server <leaf>` (ADR-004) on all three 26.3 loaders: Fabric
never dials the sentinel, Forge/NeoForge do and are re-routed.

## Reproducing the diagnosis (runbook for future agents)

This is the instrumentation that established the root cause. Keep it as a
template; every probe is tagged `[TMC-RCA]` / `TMC_DEBUG` so it is a single
grep to strip.

**1. Turn on Mixin verbose + export in the leaf's `runClient` jvmArgs**
(`buildSrc/src/main/groovy/mc-forge.gradle`, `minecraft { runs { client { ... } } }`):

```groovy
jvmArgs '-Dmixin.debug.export=true',
        '-Dmixin.debug.verbose=true'
```

The verbose banner (top of the log) reports the Mixin version, the service
(`Service=ModLauncher` vs. an agent), the detected side, and — right before the
game boots — `Preparing mixins for MixinEnvironment[<PHASE>]`. That phase line is
the first thing to read.

**2. Probe the Mixin phase and environment identity from the mod.** At
`onInitializeClient` (which runs *after* `Preparing mixins`) log:

```java
var env = org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment();
System.out.println("[TMC-RCA] modInit phase=" + env.getPhase()
        + " envId=" + System.identityHashCode(env));
```

Repeat the call from `onTick` (first fire) and from the point the target screen
is shown. A stable `envId` with a constant phase rules out "the environment
object/phase changes and deselects the config."

**3. Probe the game classes' defining classloaders.** At the same points log
`Minecraft.class`, `Screen.class`, and `JoinMultiplayerScreen.class`'
`getClassLoader()` and compare identity. Identical loaders rule out
"the screens load in a different classloader than the one Mixin is wired to."

**4. The decisive force-load probe.** At mod-init (post-prep, environment
live), `loadClass` the target screen class through the game classloader and
then check whether the Mixin's seam appeared:

```java
ClassLoader cl = Minecraft.class.getClassLoader();
Class<?> scr = cl.loadClass("net.minecraft.client.gui.screens.Screen");
boolean woven = com.tailscale.mclink.ScreenAccessor.class.isAssignableFrom(scr);
System.out.println("[TMC-RCA] afterForceload accessorPresent=" + woven);
```

`false` means loading the class post-prep does not trigger the weave — the
behaviour is not a plain "load it earlier/later" fix; the loader's Mixin
application is load-timing-sensitive for that class. (An optional companion is a
`ClassLoader.findLoadedClass(name)` check to record whether the class was
already defined before the probe; see the launcher caveat below.)

**5. Confirm which Mixins are actually live** by giving each Mixin an
observable side effect and grepping the log: an `@Inject` hook body
`System.out.println("TMC_DEBUG <hook> firing")`, and for the widget path log
whether the accessor was used or the reflection fallback. If the `Minecraft`
tick hook logs but the screen hooks do not, the asymmetry is confirmed at
runtime, not just in bytecode.

**6. Run it** with the headless smoke harness (ADR-004):

```sh
TMC_DISPLAY=:104 ./scripts/gui-smoke.sh forge-26.3 420
```

Read `/tmp/gui-smoke-forge-26.3.log` for the `[TMC-RCA]` and `TMC_DEBUG` lines
and the `Preparing mixins for MixinEnvironment[...]` line.

### Launcher caveat (do not lose this)

The Forge/NeoForge dev run is launched by the **Slime Launcher** in-process, and
it forwards `-D`/`-X` jvmArgs but **drops `--add-` flags**. Additionally the
mod's classes load as the **named module `tailcarft`**, and `--add-opens ...=ALL-UNNAMED`
only opens a package to the *unnamed* module. Both of these make
`ClassLoader.findLoadedClass` (a `java.base` protected method) unreachable via
reflection from the mod on a ModLauncher run — the call throws
`InaccessibleObjectException`. The phase/classloader/force-load probes in steps
2–5 do not need it and work fine; only the optional "was it already loaded"
check in step 4 does. If you need that check on a ModLauncher loader, you must
get `--add-opens java.base/java.lang=tailcarft` onto the actual game JVM by a
route the launcher does not filter (e.g. an agent, or a hand-built launch
command), not via the Gradle run jvmArgs.

### What is and is not the cause

Ruled **out** by the probes above, so do not re-litigate them:
- The Mixin phase (constant `DEFAULT`),
- the `MixinEnvironment` object identity (stable),
- the `MixinConfig` phase filter (the config has none; all targets are in the
  plain `client` list),
- the defining classloader (single `TransformingClassLoader[TRANSFORMER]`).

The cause **is** the loader's Mixin application being load-timing-sensitive for
late GUI-screen classes on ModLauncher, versus the agent's
load-timing-independent application on Fabric. The tick-hook design above is the
chosen workaround.

## Related

- **ADRs:** ADR-001 (exit-point hooks), ADR-002 (static mixin verification),
  ADR-003 (loader/version matrix), ADR-004 (headless GUI smoke test)
- **FDRs:** FDR-001 (sharing a singleplayer world — the Join flow this button
  serves, and the "Open to LAN" hosting trigger in the "second consequence"
  section above)
