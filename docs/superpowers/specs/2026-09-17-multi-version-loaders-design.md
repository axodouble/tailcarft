# Design: Multi-loader, multi-version build (Fabric + NeoForge + Forge, 6 MC versions)

Date: 2026-09-17
Status: approved in conversation (approach A; Forge for 1.20.1; `tailcarft-<ver>-<loader>-<mc>.jar` naming)

## Purpose

Tailcarft currently builds a single Fabric 1.21.1 jar. We want one repository to
support both Fabric and NeoForge (plus Forge, which is the only third-party
loader that exists for 1.20.1) across Minecraft versions 1.20.1, 1.21.1,
1.21.11, 26.1, 26.2, and 26.3, and to publish a jar for every supported
(loader, version) pair from a single tag.

## Scope

Build matrix (12 artifacts per release):

| Loader   | MC versions                                  |
| -------- | -------------------------------------------- |
| Fabric   | 1.20.1, 1.21.1, 1.21.11, 26.1, 26.2, 26.3    |
| NeoForge | 1.21.1, 1.21.11, 26.1, 26.2, 26.3            |
| Forge    | 1.20.1                                       |

- Artifact name per pair: `tailcarft-<mod_version>-<loader>-<mc_version>.jar`
  (e.g. `tailcarft-0.2.0-neoforge-26.3.jar`, `tailcarft-0.2.0-forge-1.20.1.jar`).
- One Forgejo release per tag containing all 12 jars plus `SHA256SUMS`.
- Go helper: unchanged; the same six native binaries are bundled into every jar.

Out of scope: other MC versions, runtime/in-game verification in CI, publishing
to Modrinth/CurseForge, snapshot MC builds.

## Repository layout

```
buildSrc/src/main/groovy/     # precompiled convention plugins:
                              #   mc-base.gradle, mc-fabric.gradle,
                              #   mc-neoforge.gradle, mc-forge.gradle
common/src/main/java/         # all mod logic; zero net.fabricmc imports
common/src/main/resources/    # assets/mclink/** (lang files + native helper binaries)
common/src/test/java/         # existing unit tests (Invites, Platform,
                              #   TailcarftConfig, HelperEvent)
loader/fabric/src/main/       # Fabric entry classes + resources/fabric.mod.json
loader/neoforge/src/main/     # NeoForge @Mod classes + resources/META-INF/neoforge.mods.toml
loader/forge/src/main/        # Forge @Mod classes + resources/META-INF/mods.toml
version/<mc>/src/main/        # per-MC overrides: mixin classes, mclink.mixins.json,
                              #   any screen/logic class whose API diverges
scripts/build-natives.sh      # unchanged
helper/                       # unchanged
```

Each leaf project compiles the union of three source roots: `common`,
`loader/<loader>`, and `version/<mc>` (same for resources and tests). A class
must live in exactly one layer (two copies of the same class across layers
would be a duplicate-class compile error): when behavior diverges for one
version or loader, that version/loader keeps its own copy of the class and the
other layers have none.

`mod/` is deleted once the move is complete.

## Build matrix and Gradle configuration

- `gradle.properties` holds the matrix and all version pins:
  - `targets.fabric=1.20.1,1.21.1,1.21.11,26.1,26.2,26.3`
  - `targets.neoforge=1.21.1,1.21.11,26.1,26.2,26.3`
  - `targets.forge=1.20.1`
  - Per-version pins, e.g. `yarn.1.21.1=1.21.1+build.3`,
    `fabricapi.1.21.1=0.115.6+1.21.1`, `neoforge.26.3=26.3.0.3-beta`,
    `forge.1.20.1=47.4.x`, `java.1.20.1=17`, `java.default=21`.
  - Exact pin values for the 26.x line (yarn build, fabric-api, fabric-loader,
    NeoForge) are resolved against the Fabric meta / NeoForge maven at
    implementation time and recorded here. The 26.x Java requirement is
    verified the same way; if any 26.x version requires a JDK newer than 21,
    `java.<mc>` records it and CI installs that JDK too.
- `settings.gradle` includes one leaf project per (loader, version) pair from
  the matrix, named `<loader>-<mc_version>` (e.g. `fabric-1.20.1`,
  `neoforge-26.3`).
- `pluginManagement.repositories` gains the NeoForge maven
  (`https://maven.neoforged.net/releases`) and the Forge maven
  (`https://maven.minecraftforge.net`) alongside the existing Fabric maven and
  Gradle plugin portal.
- `buildSrc` convention plugins:
  - `mc-base` — Java toolchain (17 for 1.20.1, 21 otherwise, overridable per
    version), source-set layering (common + loader + version), `processResources`
    filtering (`version`, `minecraft_version`, `java_version`, `loader_version`
    properties), JUnit 5 test task, `base.archivesName =
    "tailcarft-${mod_version}-${loader}-${mc_version}"` so every jar comes out
    with its final release name.
  - `mc-fabric` — applies `fabric-loom`; dependencies `com.mojang:minecraft:<v>`,
    yarn mappings, fabric-loader, fabric-api (per-version pins).
  - `mc-neoforge` — applies `net.neoforged.moddev`; dependency `net.neoforged:neoforge:<pin>`.
  - `mc-forge` — applies `net.minecraftforge.gradle`; dependency `net.minecraftforge:forge:<pin>`.
- `./gradlew build` at the root builds and tests all 12 leaves; Gradle runs
  them with the existing `org.gradle.parallel=true`.

## Code strategy

Loader entry points:

- `McLinkClient` and `McLinkServer` are removed from `common`.
- `common` gains two final hook classes with static methods only:
  `ClientMod` (`onScreenInit(client, screen, width, height)`,
  `onTick(client)`, `onClientStopping(client)`) and
  `ServerMod` (`onStarted(server)`, `onStopping()`), containing the logic
  currently in `McLinkClient`/`McLinkServer` (button wiring, state lifecycle,
  toast, `ServerHost` start/close).
- Per-loader entry classes call these hooks from their native event bus:
  - Fabric: `FabricClient implements ClientModInitializer` and
    `FabricServer implements DedicatedServerModInitializer`, registering
    `ScreenEvents.AFTER_INIT`, `ClientTickEvents.END_CLIENT_TICK`,
    `ClientLifecycleEvents.CLIENT_STOPPING`, `ServerLifecycleEvents.SERVER_STARTED`,
    `SERVER_STOPPING` (as today). Fabric API remains a dependency of Fabric
    leaves only.
  - NeoForge: `@Mod` client and server entry classes registering
    `ScreenOpenEvent`, `ClientTickEvent.Post`,
    `ClientLifecycleEvent.CLIENT_STOPPING`, `ServerStartedEvent`,
    `ServerStoppingEvent`.
  - Forge 1.20.1: `@Mod` entry classes registering the 1.20.1 equivalents
    (`ScreenOpenEvent`, client tick/stopped events, `ServerStartedEvent`,
    `ServerStoppingEvent`).

Loader API lookups:

- `FabricLoader.getInstance().getConfigDir()` in `TailcarftConfig`,
  `ScreenState`, and `NativeHelper` is replaced by a per-loader `configDir()`
  implementation (Fabric: `FabricLoader`; NeoForge/Forge: `FMLPaths.CONFIGDIR`)
  behind a single common accessor.
- `Screens.getButtons(screen)` is replaced by `screen.children()` filtering
  (the code already uses `screen.children()` for the server list widget),
  removing the last Fabric API screen utility from the shared path.
- After the refactor, `common` has no `net.fabricmc.*` imports.

Mixins and mod metadata:

- The six mixin classes and `mclink.mixins.json` start per-version:
  `version/<mc>/src/main/java/.../mixin/` + `version/<mc>/src/main/resources/mclink.mixins.json`,
  initially copied from the current 1.21.1 code, then fixed up per version as
  the build surfaces rename/signature drift (expected at least for 1.20.1;
  1.21.11 likely needs none; 26.x unknown).
- Mixin references in mod metadata: `fabric.mod.json` keeps `"mixins":
  ["mclink.mixins.json"]`; `neoforge.mods.toml` uses `mixinConfigs`;
  Forge `mods.toml` uses `[[mixins]] config = "mclink.mixins.json"`.
- Any class (mixin or otherwise) proven byte-identical across all six versions
  is moved to `common` as a post-implementation cleanup, only after every leaf
  compiles.

Resources:

- `fabric.mod.json` (loader/fabric): `version`, `minecraft` range, `java`
  minimum, and `fabricloader`/`fabric-api` dependencies filtered from properties
  per leaf.
- `neoforge.mods.toml` (loader/neoforge): mod metadata, `loaderVersion`,
  `modLoader` java, mixin config reference; MC version range filtered per leaf.
- `mods.toml` (loader/forge): same shape for Forge 1.20.1.
- Lang files and native helper assets stay in `common/src/main/resources`.

## Release and CI

`.forgejo/workflows/release.yaml` changes:

- Install Temurin JDK 17 and JDK 21 (plus any additional version the 26.x
  toolchain requires, per `java.<mc>` in `gradle.properties`); point Gradle at
  both via `org.gradle.java.installations.paths`.
- Unchanged: helper natives build, Go tests, tag-`mod_version` match check,
  pre-release detection for dashed tags.
- Java step becomes `./gradlew test build --console=plain --no-daemon` (all
  12 leaves).
- Staging copies the 12 jars from each leaf's `build/libs/` (already named per
  the convention) into `dist/release/`, writes `SHA256SUMS`, and the existing
  forgejo-release step uploads everything.
- `timeout-minutes` 45 → 90 (six Minecraft decompilations plus 12 jar builds).

README updates: supported-versions matrix table, per-target build commands
(e.g. `./gradlew :neoforge-26.3:build`), and the new jar naming in the
Releasing section.

## Verification

- Gate: `./gradlew build` succeeds for all 12 leaves on the developer machine
  and in CI (compiles + unit tests per leaf).
- Existing unit tests (Invites, Platform, TailcarftConfig, HelperEvent) run
  unchanged from `common/src/test` in every leaf.
- Jar sanity: each leaf's jar contains its mod metadata file, the mixin config,
  and the six native helper binaries.
- In-game behavior is verified manually by the maintainer; this environment
  cannot run Minecraft.

## Risks and unknowns

- Exact yarn/fabric-api/fabric-loader/NeoForge pins for 1.21.11 and the 26.x
  line, and the 26.x Java toolchain requirement: resolved against upstream
  metadata at implementation time and recorded in `gradle.properties`.
- API drift: 1.20.1 will need at least mixin/signature fixes (Screen
  constructor, button APIs); 26.x drift is unknown and may require additional
  per-version classes. The version layering contains this; worst case a version
  is temporarily dropped from its loader's `targets` list rather than blocking
  the others.
- Build weight: 12 leaves each decompile Minecraft once; caches make repeat
  builds fast, but first CI runs are long (hence the 90-minute timeout).
- ForgeGradle (1.20.1) and ModDevGradle coexisting with fabric-loom in one
  build is a proven pattern (multi-loader templates) but is new to this repo;
  the first implementation step is an empty-matrix build check before moving
  code.

## Conventions

- Every new Java, Shell, and Go source file carries the license header per
  `AGENTS.md`.
- `mod_version` in `gradle.properties` is bumped by the maintainer at release
  time, as today; the workflow's tag-match check is unchanged.
