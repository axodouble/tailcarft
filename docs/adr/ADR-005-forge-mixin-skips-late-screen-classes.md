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

The screen-targeting Mixins are kept for the behaviour they carry that is not
replicated by the tick hook (the marker server-list entry and the
join-intercept on that entry); on a ModLauncher loader those simply do not
apply, and the tick-hook button is the primary, loader-independent join path so
the feature remains usable.

**Why:** It is the smallest change that makes the button render identically on
Fabric, Forge, and NeoForge, and it degrades gracefully (accessor-when-present,
reflection-when-not) rather than crashing on a loader whose Mixin is absent.

**Tradeoff:** The widget is added from a per-tick poll guarded by screen
instance + size (so it does not re-add every tick or stack after a resize), and
the protected-method reflection path must track the `Screen` widget API per
version.

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
  serves)
