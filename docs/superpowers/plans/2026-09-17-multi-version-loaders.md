# Multi-Loader Multi-Version Build Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One Gradle build producing all 12 (loader, MC-version) Tailcarft jars — Fabric × 6 versions, NeoForge × 5, Forge × 1 — from one tag.

**Architecture:** Matrix-driven Gradle build. `settings.gradle` includes one leaf project per `(loader, mc)` pair read from `gradle.properties`; `buildSrc` precompiled convention plugins (`mc-base`, `mc-fabric`, `mc-neoforge`, `mc-forge`) configure each leaf to compile the union of three source layers: `common/` (all shared mod logic), `loader/<loader>/` (loader entry points + mod metadata), and `version/<mc>/` (per-MC screens, state, mixins). Escape hatch: `version/<mc>/src-<loader>/` is added as an extra source root only for that loader's leaf when the directory exists.

**Tech Stack:** Gradle 8.10.2 (wrapper may be bumped for the Java 25 toolchain — Task 1), fabric-loom 1.8-SNAPSHOT, ModDevGradle 2.0.147 (`net.neoforged.moddev`), ForgeGradle 6.0.54 (`net.minecraftforge.gradle`), JUnit 5.11.4, Temurin/OpenJDK 17/21/25 toolchains.

**Spec:** `docs/superpowers/specs/2026-09-17-multi-version-loaders-design.md` (commits `c889a98`, `6dc3df7`). Read it first — this plan implements it.

## Global Constraints

- **All twelve leaves compile against Mojang official mappings (mojmap). Yarn is banned everywhere** (`mappings loom.officialMojangMappings()` for Fabric; NeoForge and Forge use mojmap natively).
- `common/` has **zero `net.fabricmc.*` imports**. Loader-specific imports may only appear in `loader/<loader>/` or `version/<mc>/src-<loader>/`.
- A class lives in **exactly one** source layer (two copies = duplicate-class compile error).
- Every new Java source file starts with the exact license header (see `AGENTS.md`):

  ```java
  /*
   * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
   *
   * Use of this source code is governed by a BSD-style license that can be
   * found in the LICENSE file.
   */
  ```

- No license headers on `.gradle` build files, JSON, or TOML (per `AGENTS.md`).
- Final artifact per leaf: `tailcarft-<mod_version>-<loader>-<mc_version>.jar` in `<loader>-<mc_version>/build/libs/`.
- Toolchains: `java.1.20.1=17`, `java.1.21.1=21`, `java.1.21.11=21`, `java.26.1=25`, `java.26.2=25`, `java.26.3=25`.
- Version pins (verified 2026-09-17; re-confirm, don't re-choose, in Task 1): `fabricloader=0.19.5`; `fabricapi.1.20.1=0.92.12+1.20.1`, `fabricapi.1.21.1=0.116.17+1.21.1`, `fabricapi.1.21.11=0.141.6+1.21.11`, `fabricapi.26.1=0.145.1+26.1`, `fabricapi.26.2=0.160.0+26.2`, `fabricapi.26.3=0.160.6+26.3`; `neoforge.1.21.1=21.1.250`, `neoforge.1.21.11=21.11.45`, `neoforge.26.1=26.1.2.109`, `neoforge.26.2=26.2.0.88`, `neoforge.26.3=26.3.0.3-beta`; `forge.1.20.1=1.20.1-47.4.23`.
- `mod/` is deleted only in Task 12. Between Tasks 1 and 12 the old `mod/` tree is dead code on disk; the matrix tree is what builds.
- Local JDKs (already installed): `/usr/lib/jvm/java-17-openjdk-amd64`, `/usr/lib/jvm/java-21-openjdk-amd64`, `/usr/lib/jvm/java-25-openjdk-amd64`.
- Commits: short imperative messages, one commit per task unless the task says otherwise. Never commit `local.properties`.

---

### Task 1: Scaffold the empty 12-leaf matrix

Proves the three Gradle plugin families (loom, ModDev, ForgeGradle) coexist and the Java 17/21/25 toolchains resolve — before any code moves.

**Files:**
- Create: `buildSrc/settings.gradle`, `buildSrc/build.gradle`
- Create: `buildSrc/src/main/groovy/mc-base.gradle`
- Create: `buildSrc/src/main/groovy/mc-fabric.gradle`
- Create: `buildSrc/src/main/groovy/mc-neoforge.gradle`
- Create: `buildSrc/src/main/groovy/mc-forge.gradle`
- Modify: `settings.gradle` (full rewrite), `build.gradle` (full rewrite), `gradle.properties` (full rewrite)
- Create (untracked): `local.properties`
- Modify: `.gitignore` (add `local.properties`)

**Interfaces:**
- Consumes: nothing.
- Produces: 12 leaf projects named `<loader>-<mc>` (e.g. `fabric-26.3`, `forge-1.20.1`), each applying `mc-base` + one loader convention; property keys `targets.<loader>`, `java.<mc>`, `fabricapi.<mc>`, `neoforge.<mc>`, `forge.<mc>`, `mod_version`, `maven_group`, `fabricloader`.

- [ ] **Step 1: Write `buildSrc/settings.gradle`**

```groovy
rootProject.name = 'tailcarft-buildsrc'
```

- [ ] **Step 2: Write `buildSrc/build.gradle`**

```groovy
plugins {
    id 'groovy'
}

repositories {
    mavenCentral()
    maven { url = 'https://maven.fabricmc.net/' }
    maven { url = 'https://maven.neoforged.net/releases' }
    maven { url = 'https://maven.minecraftforge.net' }
}

dependencies {
    implementation gradleApi()
    implementation localGroovy()
    implementation 'net.fabricmc:fabric-loom:1.8-SNAPSHOT'
    implementation 'net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:2.0.147'
    implementation 'net.minecraftforge.gradle:net.minecraftforge.gradle.gradle.plugin:6.0.54'
}
```

- [ ] **Step 3: Write `buildSrc/src/main/groovy/mc-base.gradle`**

```groovy
def loader = project.name.substring(0, project.name.indexOf('-'))
def mc = project.name.substring(project.name.indexOf('-') + 1)
def javaVersion = Integer.parseInt(rootProject.findProperty("java.${mc}") ?: '21')
def modVersion = rootProject.findProperty('mod_version') as String

apply plugin: 'java'

version = modVersion
group = rootProject.findProperty('maven_group') as String

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
    withSourcesJar()
}

sourceSets {
    main {
        java.srcDirs = [
                rootProject.file('common/src/main/java'),
                rootProject.file("loader/${loader}/src/main/java"),
                rootProject.file("version/${mc}/src/main/java"),
        ]
        resources.srcDirs = [
                rootProject.file('common/src/main/resources'),
                rootProject.file("loader/${loader}/src/main/resources"),
                rootProject.file("version/${mc}/src/main/resources"),
        ]
    }
    test {
        java.srcDirs = [rootProject.file('common/src/test/java')]
    }
}

// Per-(loader, version) escape hatch, e.g. version/26.3/src-fabric/java.
if (rootProject.file("version/${mc}/src-${loader}/java").exists()) {
    sourceSets.main.java.srcDir rootProject.file("version/${mc}/src-${loader}/java")
}
if (rootProject.file("version/${mc}/src-${loader}/resources").exists()) {
    sourceSets.main.resources.srcDir rootProject.file("version/${mc}/src-${loader}/resources")
}

dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

tasks.named('test') {
    useJUnitPlatform()
}

base {
    archivesName = "tailcarft-${modVersion}-${loader}-${mc}"
}

// Jar files come out named exactly tailcarft-<mod_version>-<loader>-<mc>.jar
// (no trailing -<version> suffix); project.version still feeds resource expansion.
tasks.withType(AbstractArchiveTask).configureEach {
    archiveVersion = ''
}

processResources {
    def props = [
            version          : modVersion,
            minecraft_version: mc,
            java_version     : javaVersion.toString(),
            loader_version   : loader == 'fabric' ? rootProject.findProperty('fabricloader')
                    : loader == 'neoforge' ? rootProject.findProperty("neoforge.${mc}")
                    : rootProject.findProperty("forge.${mc}"),
    ]
    inputs.properties(props)
    filesMatching(['fabric.mod.json', 'META-INF/neoforge.mods.toml', 'META-INF/mods.toml']) {
        expand(props)
    }
}
```

- [ ] **Step 4: Write `buildSrc/src/main/groovy/mc-fabric.gradle`**

```groovy
def mc = project.name.substring(project.name.indexOf('-') + 1)

plugins {
    id 'fabric-loom'
}

dependencies {
    minecraft "com.mojang:minecraft:${mc}"
    mappings loom.officialMojangMappings()
    modImplementation "net.fabricmc:fabric-loader:${rootProject.findProperty('fabricloader')}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${rootProject.findProperty("fabricapi.${mc}")}"
}
```

- [ ] **Step 5: Write `buildSrc/src/main/groovy/mc-neoforge.gradle`**

```groovy
def mc = project.name.substring(project.name.indexOf('-') + 1)

plugins {
    id 'net.neoforged.moddev'
}

neoForge {
    version = rootProject.findProperty("neoforge.${mc}") as String
}
```

- [ ] **Step 6: Write `buildSrc/src/main/groovy/mc-forge.gradle`**

```groovy
def mc = project.name.substring(project.name.indexOf('-') + 1)

plugins {
    id 'net.minecraftforge.gradle'
}

minecraft {
    version = rootProject.findProperty("forge.${mc}") as String
    mappings channel: 'official', version: mc
}
```

- [ ] **Step 7: Rewrite the root `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        maven { url = 'https://maven.fabricmc.net/' }
        maven { url = 'https://maven.neoforged.net/releases' }
        maven { url = 'https://maven.minecraftforge.net' }
        gradlePluginPortal()
    }
}

rootProject.name = 'tailcat-for-minecraft'

def matrix = new Properties()
file('gradle.properties').withInputStream { matrix.load(it) }
['fabric', 'neoforge', 'forge'].each { loader ->
    def key = "targets.${loader}"
    if (matrix.getProperty(key) != null) {
        matrix.getProperty(key).split(',').each { mc ->
            include "${loader}-${mc.trim()}"
        }
    }
}
```

- [ ] **Step 8: Rewrite the root `build.gradle`**

```groovy
subprojects {
    apply plugin: 'mc-base'
    apply plugin: "mc-${name.substring(0, name.indexOf('-'))}"
}
```

- [ ] **Step 9: Rewrite the root `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx4G
org.gradle.parallel=true

mod_version=0.1.0
maven_group=pe.jas.tailcarft

targets.fabric=1.20.1,1.21.1,1.21.11,26.1,26.2,26.3
targets.neoforge=1.21.1,1.21.11,26.1,26.2,26.3
targets.forge=1.20.1

java.1.20.1=17
java.1.21.1=21
java.1.21.11=21
java.26.1=25
java.26.2=25
java.26.3=25

fabricloader=0.19.5
fabricapi.1.20.1=0.92.12+1.20.1
fabricapi.1.21.1=0.116.17+1.21.1
fabricapi.1.21.11=0.141.6+1.21.11
fabricapi.26.1=0.145.1+26.1
fabricapi.26.2=0.160.0+26.2
fabricapi.26.3=0.160.6+26.3

neoforge.1.21.1=21.1.250
neoforge.1.21.11=21.11.45
neoforge.26.1=26.1.2.109
neoforge.26.2=26.2.0.88
neoforge.26.3=26.3.0.3-beta

forge.1.20.1=1.20.1-47.4.23
```

(`-Xmx4G` up from `-Xmx2G`: twelve parallel leaves each decompile Minecraft.)

- [ ] **Step 10: Create untracked `local.properties` and gitignore it**

```properties
org.gradle.java.installations.paths=/usr/lib/jvm/java-17-openjdk-amd64,/usr/lib/jvm/java-21-openjdk-amd64,/usr/lib/jvm/java-25-openjdk-amd64
```

Append to `.gitignore`:

```
# Local machine-specific Gradle settings (JDK installation paths)
local.properties
```

- [ ] **Step 11: Verify all 12 leaf projects resolve**

Run: `./gradlew projects --console=plain`
Expected: project list contains exactly `fabric-1.20.1`, `fabric-1.21.1`, `fabric-1.21.11`, `fabric-26.1`, `fabric-26.2`, `fabric-26.3`, `neoforge-1.21.1`, `neoforge-1.21.11`, `neoforge-26.1`, `neoforge-26.2`, `neoforge-26.3`, `forge-1.20.1`. (Note: `mod` is gone — expected, the old module is dead until Task 12 removes its directory.)

- [ ] **Step 12: Build one Fabric leaf (first loom decompile, takes minutes)**

Run: `./gradlew :fabric-1.21.1:build --console=plain`
Expected: `BUILD SUCCESSFUL`, producing `fabric-1.21.1/build/libs/tailcarft-0.1.0-fabric-1.21.1.jar`.

Contingency A — if the build fails on `loom.officialMojangMappings()` (unknown method in this loom build):
```sh
jar=$(find ~/.gradle/caches -name "fabric-loom-1.8*.jar" ! -name "*sources*" | head -1)
javap -cp "$jar" net.fabricmc.loom.LoomGradleExtension | grep -i mojang
```
Use the method name it reports (e.g. `officialMojangMappings`) in `mc-fabric.gradle`; if no such method exists, replace the line with `mappings "net.minecraft:minecraft:${mc}:named"` and rebuild.

Contingency B — if it fails with a Java 25 toolchain error on the 26.x leaves (or later in Step 13): bump the wrapper:
```sh
curl -s https://services.gradle.org/versions/current
./gradlew wrapper --gradle-version <that-version>
```
Then re-run. If ForgeGradle 6.0.54 then breaks under the newer Gradle, set `targets.forge=` (empty) in `gradle.properties` temporarily, finish the matrix, and restore `targets.forge=1.20.1` at the end of this task; if it still fails, keep it empty and record the blocker — the spec's fallback is dropping a target rather than blocking others.

- [ ] **Step 13: Build all 12 empty leaves**

Run: `./gradlew build --console=plain`
Expected: `BUILD SUCCESSFUL` for all 12 leaves (this performs the first NeoForm/Forge decompilations; expect a long first run).

Contingency C — buildSrc classpath conflict between the three plugin families (e.g. duplicated/incompatible classes at configuration time): move plugin application out of buildSrc. Delete the three plugin marker/loom dependencies from `buildSrc/build.gradle` and the `plugins {}` blocks from the three loader convention files, and give each leaf its own build file, e.g. `fabric-1.21.1/build.gradle`:
```groovy
plugins {
    id 'fabric-loom' version '1.8-SNAPSHOT'
}
apply plugin: 'mc-base'
apply plugin: 'mc-fabric'
```
(analogous `net.neoforged.moddev` version `2.0.147` / `net.minecraftforge.gradle` version `6.0.54` files for the other leaves; the `pluginManagement` repositories in `settings.gradle` already resolve them), and remove the `subprojects {}` block from the root `build.gradle`. Re-run Step 13.

- [ ] **Step 14: Commit**

```bash
git add buildSrc settings.gradle build.gradle gradle.properties .gitignore
git commit -m "build: scaffold 12-leaf loader/version matrix"
```

---

### Task 2: Common layer — shared logic, runtime env, tests

Moves every loader-agnostic class into `common/`, replaces the last `FabricLoader` lookups with the `GameRuntime`/`RuntimeEnv` seam, and renames the one shared MC-API class to mojmap.

**Files:**
- Create: `common/src/main/java/com/tailscale/mclink/Invites.java` (copied unchanged)
- Create: `common/src/main/java/com/tailscale/mclink/HelperEvent.java` (copied unchanged)
- Create: `common/src/main/java/com/tailscale/mclink/HelperProcess.java` (copied unchanged)
- Create: `common/src/main/java/com/tailscale/mclink/Platform.java` (copied unchanged)
- Create: `common/src/main/java/com/tailscale/mclink/NativeHelper.java` (copied, 3 lookups replaced)
- Create: `common/src/main/java/com/tailscale/mclink/TailcarftConfig.java` (copied, 2 lookups replaced)
- Create: `common/src/main/java/com/tailscale/mclink/ServerHost.java` (copied unchanged)
- Create: `common/src/main/java/com/tailscale/mclink/TailcarftServerEntry.java` (rewritten in mojmap)
- Create: `common/src/main/java/com/tailscale/mclink/RuntimeEnv.java`
- Create: `common/src/main/java/com/tailscale/mclink/GameRuntime.java`
- Create: `common/src/main/java/com/tailscale/mclink/ServerMod.java` (new static hook)
- Create: `common/src/main/resources/assets/mclink/lang/` (copied from `mod/`)
- Create: `common/src/test/java/com/tailscale/mclink/InvitesTest.java`, `HelperEventTest.java`, `PlatformTest.java`, `TailcarftConfigTest.java` (copied unchanged)
- Create: `loader/fabric/src/main/resources/fabric.mod.json` (Fabric mod metadata; lands here early so a leaf is buildable — its Java entry classes arrive in Task 3)

**Interfaces:**
- Consumes: Task 1's leaf projects.
- Produces (used by every later task):
  - `com.tailscale.mclink.RuntimeEnv` — interface with `Path gameDir()`, `Path configDir()`, `boolean isDevelopment()`, `String modVersion()`.
   - `com.tailscale.mclink.GameRuntime` — `static void init(RuntimeEnv)`, `static RuntimeEnv get()` (throws `IllegalStateException` if uninitialized).
   - `com.tailscale.mclink.ServerMod` — `static void onStarted(MinecraftServer)`, `static void onStopping()` (wraps a single shared `ServerHost`).
  - `TailcarftServerEntry.create(TailcarftConfig)` returns `net.minecraft.client.network.ServerData`; `TailcarftServerEntry.MARKER` is `String`.
  - `ServerHost` — `void start(MinecraftServer)`, `void close()` (unchanged API).
  - `ScreenState` is NOT created here (Task 3, version layer). `ClientMod` is NOT created here (Task 3).

- [ ] **Step 1: Copy the unchanged pure classes, lang assets, and tests**

```bash
mkdir -p common/src/main/java/com/tailscale/mclink \
         common/src/test/java/com/tailscale/mclink \
         common/src/main/resources/assets/mclink
cp mod/src/main/java/com/tailscale/mclink/Invites.java \
   mod/src/main/java/com/tailscale/mclink/HelperEvent.java \
   mod/src/main/java/com/tailscale/mclink/HelperProcess.java \
   mod/src/main/java/com/tailscale/mclink/Platform.java \
   common/src/main/java/com/tailscale/mclink/
cp mod/src/test/java/com/tailscale/mclink/*.java common/src/test/java/com/tailscale/mclink/
cp -r mod/src/main/resources/assets/mclink/lang common/src/main/resources/assets/mclink/
cp mod/src/main/java/com/tailscale/mclink/ServerHost.java common/src/main/java/com/tailscale/mclink/
```

- [ ] **Step 2: Write `common/src/main/java/com/tailscale/mclink/RuntimeEnv.java`**

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import java.nio.file.Path;

public interface RuntimeEnv {
    Path gameDir();

    Path configDir();

    boolean isDevelopment();

    String modVersion();
}
```

- [ ] **Step 3: Write `common/src/main/java/com/tailscale/mclink/GameRuntime.java`**

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

public final class GameRuntime {
    private static RuntimeEnv env;

    private GameRuntime() {}

    public static void init(RuntimeEnv value) {
        env = value;
    }

    public static RuntimeEnv get() {
        if (env == null) {
            throw new IllegalStateException("GameRuntime is not initialized");
        }
        return env;
    }
}
```

- [ ] **Step 4: Write `common/src/main/java/com/tailscale/mclink/ServerMod.java`**

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.server.MinecraftServer;

public final class ServerMod {
    private static final ServerHost HOST = new ServerHost();

    private ServerMod() {}

    public static void onStarted(MinecraftServer server) {
        HOST.start(server);
    }

    public static void onStopping() {
        HOST.close();
    }
}
```

`MinecraftServer` here is the dedicated-server class `net.minecraft.server.MinecraftServer` (mojmap), the same type `ServerHost.start` already accepts.

- [ ] **Step 5: Create `common/.../NativeHelper.java` with the loader lookups replaced**

Copy the file, then make exactly these changes (remove the `import net.fabricmc.loader.api.FabricLoader;` line):

1. Replace
   `if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {`
   with
   `if (!GameRuntime.get().isDevelopment()) {`
2. Replace
   ```java
        String version = FabricLoader.getInstance().getModContainer("tailcarft").orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
   ```
   with
   ```java
        String version = GameRuntime.get().modVersion();
   ```
3. Replace
   `Path dir = FabricLoader.getInstance().getGameDir().resolve("tailcarft").resolve(version)`
   with
   `Path dir = GameRuntime.get().gameDir().resolve("tailcarft").resolve(version)`

- [ ] **Step 6: Create `common/.../TailcarftConfig.java` with the loader lookups replaced**

Copy the file, remove the `import net.fabricmc.loader.api.FabricLoader;` line, and in both `load()` and `loadIcon(String)`, replace
`FabricLoader.getInstance().getGameDir().resolve("config")`
with
`GameRuntime.get().configDir()`
(so `load()` reads `GameRuntime.get().configDir().resolve("mclink.json")` and `loadIcon(String fileName)` delegates to `loadIcon(GameRuntime.get().configDir(), fileName)`).

- [ ] **Step 7: Write `common/src/main/java/com/tailscale/mclink/TailcarftServerEntry.java` in mojmap**

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.network.ServerData;
import net.minecraft.network.chat.Component;

public final class TailcarftServerEntry {
    public static final String MARKER = "mclink:tailcarft";

    private TailcarftServerEntry() {}

    public static ServerData create(TailcarftConfig config) {
        ServerData info = new ServerData(config.name(), MARKER, ServerData.ServerType.OTHER);
        info.setStatus(ServerData.Status.SUCCESSFUL);
        info.ping = 1;
        info.label = Component.translatable("mclink.server.label");
        info.playerCountLabel = Component.empty();
        byte[] icon = TailcarftConfig.loadIcon(config.icon());
        if (icon != null) {
            info.setFavicon(icon);
        }
        return info;
    }
}
```

Note: `ServerData.ServerType` / `ServerData.Status` nesting and the public fields `ping`, `label`, `playerCountLabel` are confirmed by the Task 1 decompile cache; if the compiler disagrees with a name, grep the decompiled source (Task 3's Step 1 shows the command pattern) and use the name found — never change the logic.

- [ ] **Step 8: Write `loader/fabric/src/main/resources/fabric.mod.json`**

This is the shared Fabric mod descriptor for every Fabric leaf. `version`,
`minecraft_version`, and `java_version` are expanded per leaf by `mc-base`'s
`processResources`. The entrypoint classes (`FabricClient`/`FabricServer`) do
not exist until Task 3 — that is fine, because `test`/`compileTestJava` do not
resolve entrypoints (loom checks them only at `remapJar`/`build`).

```json
{
  "schemaVersion": 1,
  "id": "tailcarft",
  "version": "${version}",
  "name": "Tailcarft",
  "description": "Share Minecraft worlds through Tailcarft's userspace WireGuard transport.",
  "authors": ["Tailscale", "Jasper \"Axodouble\" V."],
  "license": "BSD-3-Clause",
  "environment": "*",
  "entrypoints": {
    "client": ["com.tailscale.mclink.FabricClient"],
    "server": ["com.tailscale.mclink.FabricServer"]
  },
  "mixins": ["mclink.mixins.json"],
  "depends": {
    "fabricloader": ">=0.16.10",
    "minecraft": "~${minecraft_version}",
    "java": ">=${java_version}",
    "fabric-api": "*"
  }
}
```

`mclink.mixins.json` is referenced but not created until Task 3 (version layer);
`test` does not resolve mixin configs, so this does not block Step 9. If this
loom build does resolve it at `test` and fails, temporarily comment out the
`"mixins"` line and restore it in Task 3.

- [ ] **Step 9: Run the common tests in one leaf**

Run: `./gradlew :fabric-1.21.1:test --console=plain`
Expected: `BUILD SUCCESSFUL`; the 4 test classes (InvitesTest, HelperEventTest, PlatformTest, TailcarftConfigTest) pass.

- [ ] **Step 10: Commit**

```bash
git add common loader
git commit -m "refactor: move shared logic into common layer behind RuntimeEnv"
```

---
### Task 3: First green leaf — `fabric-1.21.1` (the mojmap reference)

Lands the Fabric Java entry points and the 1.21.1 version layer (screens, state,
mixins) rewritten from yarn to mojmap, and makes `:fabric-1.21.1:build` green.
This is the reference implementation: every later version/loader copies from it.

**Files (all new):**
- Create: `loader/fabric/src/main/java/com/tailscale/mclink/FabricClient.java`
- Create: `loader/fabric/src/main/java/com/tailscale/mclink/FabricServer.java`
- Create: `loader/fabric/src/main/java/com/tailscale/mclink/FabricRuntimeEnv.java`
- Create: `version/1.21.1/src/main/java/com/tailscale/mclink/ClientMod.java`
- Create: `version/1.21.1/src/main/java/com/tailscale/mclink/ScreenState.java`
- Create: `version/1.21.1/src/main/java/com/tailscale/mclink/ShareScreen.java`
- Create: `version/1.21.1/src/main/java/com/tailscale/mclink/JoinRemoteScreen.java`
- Create: `version/1.21.1/src/main/java/com/tailscale/mclink/mixin/IntegratedServerAccessor.java`
- Create: `version/1.21.1/src/main/java/com/tailscale/mclink/mixin/ServerListAccessor.java`
- Create: `version/1.21.1/src/main/java/com/tailscale/mclink/mixin/ServerListMixin.java`
- Create: `version/1.21.1/src/main/java/com/tailscale/mclink/mixin/ConnectScreenMixin.java`
- Create: `version/1.21.1/src/main/java/com/tailscale/mclink/mixin/MultiplayerScreenMixin.java`
- Create: `version/1.21.1/src/main/resources/mclink.mixins.json`

**Interfaces:**
- Consumes: `common` (Task 2) — `GameRuntime`, `RuntimeEnv`, `ServerHost`,
  `ServerMod`, `HelperEvent`, `HelperProcess`, `TailcarftConfig`,
  `TailcarftServerEntry`, `ServerData`-based entry.
- Produces (used by Task 4 onward):
  - `ClientMod` — `static void onInitializeClient()`, `static void onScreenInit(Minecraft, Screen, int, int)`,
    `static void onTick(Minecraft)`, `static void onClientStopping(Minecraft)`, `static ScreenState state()`.
  - `ScreenState` — `share(Minecraft)`, `join(Minecraft, Screen, String)`, `tick(Minecraft)`, `stop()`, `takeError()`, `close()`.
  - Loader entry points named `com.tailscale.mclink.FabricClient` / `FabricServer` (matching `fabric.mod.json`).

> **Mojmap name map (yarn → mojmap).** Apply to every file in this task.
> `MinecraftClient`→`Minecraft` (`net.minecraft.client`), `DrawContext`→`GuiGraphics`
> (`net.minecraft.client.gui`), `Text`→`Component` (`net.minecraft.network.chat`),
> `ButtonWidget`→`Button`, `TextFieldWidget`→`EditBox` (both `net.minecraft.client.gui.widget`),
> `ServerInfo`→`ServerData` (`net.minecraft.client.network`),
> `GameMenuScreen`→`IngameMenu` (`net.minecraft.client.gui.screen`),
> `MultiplayerScreen`→`MultiplayerList` (`net.minecraft.client.gui.screen.multiplayer`).
> Unchanged: `Screen`, `ConnectScreen`, `ServerAddress`, `ServerList`, `CookieStorage`,
> `SystemToast`, `NetworkUtils`, `MultiplayerServerListWidget`, `IntegratedServer`,
> `MinecraftServer`. `Screens.getButtons(screen)` (Fabric API) is replaced by
> `screen.children()` filtering (loader-agnostic).

> **Decompile-grep helper.** After the first build attempt loom has decompiled
> Minecraft. To resolve any name the compiler rejects, locate the decompiled
> source and grep it — use the name found, never change the logic:
>
> ```sh
> SRC=$(find ~/.gradle/caches version/1.21.1/.gradle -type f -name "ConnectScreen.java" 2>/dev/null | head -1)
> grep -n "connect(" "$SRC"
> ```
>
> Swap `ConnectScreen.java` / the pattern for whatever member is in question.

- [ ] **Step 1: Write `loader/fabric/src/main/java/com/tailscale/mclink/FabricClient.java`**

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

public final class FabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GameRuntime.init(new FabricRuntimeEnv());
        ClientMod.onInitializeClient();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ClientMod.onScreenInit(client, screen, width, height));
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientMod.onTick(client));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientMod.onClientStopping(client));
    }
}
```

- [ ] **Step 2: Write `loader/fabric/src/main/java/com/tailscale/mclink/FabricServer.java`**

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class FabricServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        GameRuntime.init(new FabricRuntimeEnv());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ServerMod.onStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerMod.onStopping());
    }
}
```

- [ ] **Step 3: Write `loader/fabric/src/main/java/com/tailscale/mclink/FabricRuntimeEnv.java`**

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class FabricRuntimeEnv implements RuntimeEnv {
    @Override public Path gameDir() { return FabricLoader.getInstance().getGameDir(); }

    @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }

    @Override public boolean isDevelopment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }

    @Override public String modVersion() {
        return FabricLoader.getInstance().getModContainer("tailcarft").orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
    }
}
```

- [ ] **Step 4: Write `version/1.21.1/src/main/java/com/tailscale/mclink/ClientMod.java`**

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.IngameMenu;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerList;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.Button;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.network.chat.Component;

public final class ClientMod {
    private static ScreenState state;

    private ClientMod() {}

    public static void onInitializeClient() {
        state = new ScreenState();
    }

    public static ScreenState state() {
        return state;
    }

    public static void onScreenInit(Minecraft client, Screen screen, int width, int height) {
        if (screen instanceof IngameMenu && client.isIntegratedServerRunning()) {
            addShareButton(client, screen);
        } else if (screen instanceof MultiplayerList) {
            addConnectButton(client, screen, width, height);
        }
    }

    public static void onTick(Minecraft client) {
        state.tick(client);
        String error = state.takeError();
        if (error != null) {
            SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                    Component.literal("Remote LAN connection failed"), Component.literal(error));
        }
    }

    public static void onClientStopping(Minecraft client) {
        state.close();
    }

    private static void addShareButton(Minecraft client, Screen screen) {
        Button lan = findButton(screen, "menu.shareToLan");
        if (lan == null) {
            return;
        }
        int newY = lan.getY() + lan.getHeight() + 4;
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getY() > lan.getY())
                .forEach(button -> button.setY(button.getY() + 24));
        screen.addDrawable(Button.builder(Component.translatable("mclink.share"),
                        button -> client.setScreen(new ShareScreen(screen)))
                .bounds(lan.getX(), newY, lan.getWidth(), lan.getHeight()).build());
    }

    private static void addConnectButton(Minecraft client, Screen screen, int width, int height) {
        Button direct = findButton(screen, "selectServer.direct");
        if (direct == null) {
            return;
        }
        int firstRowY = direct.getY();
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getY() == firstRowY)
                .forEach(button -> button.setY(button.getY() - 24));
        for (var child : screen.children()) {
            if (child instanceof MultiplayerServerListWidget list) {
                list.setDimensionsAndPosition(width, height - 120, 0, 32);
                break;
            }
        }
        screen.addDrawable(Button.builder(Component.translatable("mclink.join"),
                        button -> client.setScreen(new JoinRemoteScreen(screen)))
                .bounds(width / 2 - 102, firstRowY, 204, 20).build());
    }

    private static Button findButton(Screen screen, String translationKey) {
        String label = Component.translatable(translationKey).getString();
        return screen.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label))
                .findFirst().orElse(null);
    }
}
```

> Verify-by-build names in this file (decompile-grep if the compiler rejects one):
> `Minecraft.isIntegratedServerRunning()`, `Screen.addDrawable(...)`,
> `Button.builder(Component, PressAction).bounds(int,int,int,int)`,
> `MultiplayerServerListWidget.setDimensionsAndPosition(int,int,int,int)`,
> `Button.getMessage()`.

- [ ] **Step 5: Write `version/1.21.1/src/main/java/com/tailscale/mclink/ScreenState.java`**

Copy `mod/src/main/java/com/tailscale/mclink/ScreenState.java` and apply: rename
`MinecraftClient`→`Minecraft`, `ServerInfo`→`ServerData` (and
`ServerInfo.ServerType.OTHER`→`ServerData.ServerType.OTHER`); remove
`import net.fabricmc.loader.api.FabricLoader;`; and in
`enableDevelopmentOfflineAuth` replace
`FabricLoader.getInstance().isDevelopmentEnvironment()` with
`GameRuntime.get().isDevelopment()`. The `ConnectScreen.connect(parent, client, address, info, false, null)`
call keeps its 6-argument form (last arg `null` = `CookieStorage`).

> Verify-by-build: `Minecraft.execute(Runnable)`, `Minecraft.getServer()` returns
> `IntegratedServer`, `ConnectScreen.connect(Screen, Minecraft, ServerAddress, ServerData, boolean, CookieStorage)`.

- [ ] **Step 6: Write `version/1.21.1/src/main/java/com/tailscale/mclink/ShareScreen.java`**

Copy `mod/.../ShareScreen.java` and apply the name map: `DrawContext`→`GuiGraphics`,
`ButtonWidget`→`Button`, `Text`→`Component`; `Button.builder(...).dimensions(...)`→
`.bounds(...)`; `McLinkClient.state()`→`ClientMod.state()`. Keep the 4-arg
`renderBackground(context, mouseX, mouseY, delta)`.

- [ ] **Step 7: Write `version/1.21.1/src/main/java/com/tailscale/mclink/JoinRemoteScreen.java`**

Copy `mod/.../JoinRemoteScreen.java` and apply the name map: `DrawContext`→`GuiGraphics`,
`ButtonWidget`→`Button`, `TextFieldWidget`→`EditBox`, `Text`→`Component`;
`new TextFieldWidget(...)`→`new EditBox(...)`; `.dimensions(...)`→`.bounds(...)`;
`McLinkClient.state()`→`ClientMod.state()`. Keep the 4-arg `renderBackground`.

- [ ] **Step 8: Write the five mixin classes in `version/1.21.1/src/main/java/com/tailscale/mclink/mixin/`**

Copy the five files from `mod/.../mixin/` and apply the name map. Specifics:

- `IntegratedServerAccessor` — `@Accessor("lanPort")` on `IntegratedServer`.
- `ServerListAccessor` — `@Accessor("servers")` returning `List<ServerData>`.
- `ServerListMixin` — `ServerInfo`→`ServerData`; targets `swapEntries(II)V` and
  `saveFile()V` on `ServerList`.
- `ConnectScreenMixin` — `MinecraftClient`→`Minecraft`, `ServerInfo`→`ServerData`;
  update the two `method = "connect(...)"` descriptors to the mojmap 4-arg and 6-arg
  `ConnectScreen.connect` signatures (grep `ConnectScreen.java`).
- `MultiplayerScreenMixin` — `@Mixin(MultiplayerList.class)` (was `MultiplayerScreen`);
  `ButtonWidget`→`Button`; `ServerInfo`→`ServerData`; targets `init()V`,
  `connect(Lnet/minecraft/client/network/ServerData;)V`, `updateButtonActivationStates()V`;
  `@Shadow` fields `serverListWidget`, `serverList`, `buttonEdit`, `buttonDelete`.

> These are the highest-risk files. After the build, decompile-grep each target
> class for the exact field/method name and descriptor before editing. Never
> change injection logic — only names/descriptors.

- [ ] **Step 9: Write `version/1.21.1/src/main/resources/mclink.mixins.json`**

```json
{
  "required": true,
  "package": "com.tailscale.mclink.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": [
    "IntegratedServerAccessor",
    "ServerListAccessor",
    "ServerListMixin",
    "MultiplayerScreenMixin",
    "ConnectScreenMixin"
  ],
  "injectors": { "defaultRequire": 1 }
}
```

- [ ] **Step 10: Build the leaf to green**

Run: `./gradlew :fabric-1.21.1:build --console=plain`
Expected: `BUILD SUCCESSFUL`, producing `fabric-1.21.1/build/libs/tailcarft-0.1.0-fabric-1.21.1.jar`.

Work every compile/mixin error with the decompile-grep helper: find the decompiled
member, copy its exact name/descriptor into the file, rebuild. Iterate until green.
If a name has no mojmap equivalent in 1.21.1, re-read the decompiled class and adapt
the call — logic stays identical.

- [ ] **Step 11: Commit**

```bash
git add loader/fabric version/1.21.1
git commit -m "feat: fabric 1.21.1 leaf on Mojang mappings"
```

---

### Task 4: `fabric-1.21.11` leaf

Replicates the 1.21.1 version layer for 1.21.11 and fixes whatever the build
surfaces (expected: minimal — 1.21.11 is a late 1.21.x).

**Files:**
- Create: `version/1.21.11/src/main/java/com/tailscale/mclink/` (copy of `version/1.21.1/...`)
- Create: `version/1.21.11/src/main/resources/mclink.mixins.json` (copy of 1.21.1)

**Interfaces:**
- Consumes: `common` + `loader/fabric` (already built) + the 1.21.1 reference.
- Produces: a green `:fabric-1.21.11:build`.

- [ ] **Step 1: Copy the 1.21.1 version layer to 1.21.11**

```bash
cp -r version/1.21.1 version/1.21.11
```

- [ ] **Step 2: Build and fix drift**

Run: `./gradlew :fabric-1.21.11:build --console=plain`
Expected: `BUILD SUCCESSFUL`. If a member differs in 1.21.11 (e.g. a changed
`renderBackground`/button/widget signature or a mixin descriptor), decompile-grep
`version/1.21.11`'s decompiled Minecraft sources and adjust only that file. Keep
`mclink.mixins.json` at `JAVA_21`.

- [ ] **Step 3: Commit**

```bash
git add version/1.21.11
git commit -m "feat: fabric 1.21.11 leaf"
```

---
### Task 5: `fabric-1.20.1` leaf (known-divergent)

1.20.1 is the oldest target and diverges most from the 1.21.1 reference: the
`Screen` constructor, the `renderBackground` signature, the `ConnectScreen.connect`
arity (no `CookieStorage`), and the button-builder method name. Fix each with
decompile-grep. Java toolchain is 17 (already set in Task 1).

**Files:**
- Create: `version/1.20.1/src/main/java/com/tailscale/mclink/` (copy of `version/1.21.1/...`, then edited)
- Create: `version/1.20.1/src/main/resources/mclink.mixins.json`

**Interfaces:**
- Consumes: `common` + `loader/fabric` + the 1.21.1 reference.
- Produces: a green `:fabric-1.20.1:build`.

- [ ] **Step 1: Copy the 1.21.1 version layer to 1.20.1**

```bash
cp -r version/1.21.1 version/1.20.1
```

- [ ] **Step 2: Set the 1.20.1 mixin compatibility level**

In `version/1.20.1/src/main/resources/mclink.mixins.json`, change
`"compatibilityLevel": "JAVA_21"` to `"compatibilityLevel": "JAVA_17"`.

- [ ] **Step 3: Build and fix the known drift points**

Run: `./gradlew :fabric-1.20.1:build --console=plain`
Work every error with the decompile-grep helper against `version/1.20.1`'s
decompiled sources. Expected fixes (confirm each against the decompile, do not
guess):

- **`Screen` constructor** — `ShareScreen`/`JoinRemoteScreen` call
  `super(Component.translatable(...))` and `MultiplayerScreenMixin` calls
  `super(null)`. Grep the 1.20.1 `Screen` constructor and adapt all three call
  sites to its actual parameter list (e.g. `(Minecraft client, Component title)`
  vs `(Component title)`).
- **`renderBackground`** — 1.20.1 is the 1-arg form. Grep `Screen.renderBackground`
  and update both screen `render(...)` bodies to match (drop `mouseX, mouseY, delta`
  if it is 1-arg).
- **`ConnectScreen.connect`** — 1.20.1 has no `CookieStorage` argument. Update
  `ScreenState.join(...)` to the 5-arg call and both `ConnectScreenMixin`
  descriptors to the 1.20.1 signatures.
- **Button builder** — confirm whether 1.20.1 mojmap uses `.bounds(...)` or
  `.dimensions(...)` on the `Button.Builder` and whether `Screen.addDrawable` is
  accessible; adjust `ClientMod` accordingly.

- [ ] **Step 4: Commit**

```bash
git add version/1.20.1
git commit -m "feat: fabric 1.20.1 leaf"
```

---

### Task 6: `fabric-26.1` leaf (build-driven)

26.x is a far newer Minecraft; drift is unknown. Pure copy + build + fix.
Java toolchain is 25 (already set in Task 1).

**Files:**
- Create: `version/26.1/src/main/java/com/tailscale/mclink/` (copy of `version/1.21.1/...`)
- Create: `version/26.1/src/main/resources/mclink.mixins.json`

**Interfaces:**
- Consumes: `common` + `loader/fabric` + the 1.21.1 reference.
- Produces: a green `:fabric-26.1:build`.

- [ ] **Step 1: Copy the 1.21.1 version layer to 26.1**

```bash
cp -r version/1.21.1 version/26.1
```

- [ ] **Step 2: Build and fix drift**

Run: `./gradlew :fabric-26.1:build --console=plain`
Expected: `BUILD SUCCESSFUL`. 26.x may rename/move `net.minecraft` members or change
screen/widget/mixin signatures; fix each with the decompile-grep helper against
`version/26.1`'s sources. Keep `mclink.mixins.json` at `JAVA_21` (Mixin has no
`JAVA_25` constant; `JAVA_21` is a valid lower bound for a Java 25 runtime).
If a whole class has no 26.x equivalent, adapt the logic to the decompiled API —
behavior must stay identical.

- [ ] **Step 3: Commit**

```bash
git add version/26.1
git commit -m "feat: fabric 26.1 leaf"
```

---

### Task 7: `fabric-26.2` leaf (build-driven)

Same procedure as Task 6 for 26.2.

**Files:**
- Create: `version/26.2/src/main/java/com/tailscale/mclink/` (copy of `version/1.21.1/...`)
- Create: `version/26.2/src/main/resources/mclink.mixins.json`

**Interfaces:**
- Consumes: `common` + `loader/fabric` + the 1.21.1 reference (and the 26.1 fixes
  as a reference for likely 26.x drift).
- Produces: a green `:fabric-26.2:build`.

- [ ] **Step 1: Copy the 1.21.1 version layer to 26.2**

```bash
cp -r version/1.21.1 version/26.2
```

- [ ] **Step 2: Build and fix drift**

Run: `./gradlew :fabric-26.2:build --console=plain`
Expected: `BUILD SUCCESSFUL`. Reuse the 26.1 fixes as a starting point, then
decompile-grep `version/26.2` for anything that still differs.

- [ ] **Step 3: Commit**

```bash
git add version/26.2
git commit -m "feat: fabric 26.2 leaf"
```

---

### Task 8: `fabric-26.3` leaf (build-driven)

Same procedure as Task 6 for 26.3.

**Files:**
- Create: `version/26.3/src/main/java/com/tailscale/mclink/` (copy of `version/1.21.1/...`)
- Create: `version/26.3/src/main/resources/mclink.mixins.json`

**Interfaces:**
- Consumes: `common` + `loader/fabric` + the 1.21.1 reference (and 26.1/26.2 fixes).
- Produces: a green `:fabric-26.3:build`.

- [ ] **Step 1: Copy the 1.21.1 version layer to 26.3**

```bash
cp -r version/1.21.1 version/26.3
```

- [ ] **Step 2: Build and fix drift**

Run: `./gradlew :fabric-26.3:build --console=plain`
Expected: `BUILD SUCCESSFUL`. Reuse the 26.1/26.2 fixes as a starting point, then
decompile-grep `version/26.3` for anything that still differs.

- [ ] **Step 3: Commit**

```bash
git add version/26.3
git commit -m "feat: fabric 26.3 leaf"
```

> **Fabric complete.** After Task 8, all six `fabric-*` leaves build. The version
> layer now exists for all six MC versions; Tasks 9–11 add the NeoForge and Forge
> loader layers on top of the same `common` + `version/<mc>` sources.

---
### Task 9: NeoForge loader layer + `neoforge-1.21.1` green

Adds the NeoForge entry (`@Mod` class + `RuntimeEnv` + `neoforge.mods.toml`) and
makes `:neoforge-1.21.1:build` green, reusing the already-green `common` and
`version/1.21.1` sources. The `@Mod` event registration is the drift-prone part
(its API changes across NeoForge versions); it is shared in `loader/neoforge`
first and split per-version in Task 10 if a later NeoForge build rejects it.

**Files (all new):**
- Create: `loader/neoforge/src/main/resources/META-INF/neoforge.mods.toml`
- Create: `loader/neoforge/src/main/java/com/tailscale/mclink/NeoForgeRuntimeEnv.java`
- Create: `loader/neoforge/src/main/java/com/tailscale/mclink/TailcarftMod.java`

**Interfaces:**
- Consumes: `common` (incl. `ServerMod`, `ClientMod` via version layer) + `version/1.21.1`.
- Produces: `com.tailscale.mclink.TailcarftMod` (`@Mod("tailcarft")`), a green
  `:neoforge-1.21.1:build`.

> **NeoForge decompile-grep.** For `net.minecraft` members use the Task 3 helper.
> For NeoForge/FML API classes, find the NeoForge sources jar and grep it:
>
> ```sh
> JAR=$(find ~/.gradle/caches -name "neoforge-*-sources.jar" 2>/dev/null | head -1)
> unzip -l "$JAR" | grep -i "ScreenEvent\|ClientTickEvent\|ServerStartedEvent"
> ```
>
> Open the listed `.java` entry and copy the exact class/member names.

- [ ] **Step 1: Write `loader/neoforge/src/main/resources/META-INF/neoforge.mods.toml`**

```toml
modLoader = "javafml"
loaderVersion = "[47,)"
license = "BSD-3-Clause"

[[mods]]
modId = "tailcarft"
version = "${version}"
displayName = "Tailcarft"
description = "Share Minecraft worlds through Tailcarft's userspace WireGuard transport."

[[mixins]]
config = "mclink.mixins.json"
```

> `loaderVersion` is the FML loader range, not the NeoForge version; confirm the
> value for 1.21.1 from a reference NeoForge 1.21.1 mod (or the ModDevGradle
> template) and adjust. `version` is expanded by `mc-base`.

- [ ] **Step 2: Write `loader/neoforge/src/main/java/com/tailscale/mclink/NeoForgeRuntimeEnv.java`**

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class NeoForgeRuntimeEnv implements RuntimeEnv {
    @Override public Path gameDir() { return FMLPaths.GAMEDIR.get(); }

    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }

    @Override public boolean isDevelopment() { return !net.neoforged.fml.loading.FMLEnvironment.production; }

    @Override public String modVersion() {
        return ModList.get().getModContainerById("tailcarft").orElseThrow()
                .getModInfo().getVersion();
    }
}
```

> Verify-by-build: `FMLPaths.GAMEDIR`/`CONFIGDIR`, `FMLEnvironment.production`,
> `ModList.get().getModContainerById(...).getModInfo().getVersion()`.

- [ ] **Step 3: Write `loader/neoforge/src/main/java/com/tailscale/mclink/TailcarftMod.java`**

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(TailcarftMod.MODID)
public class TailcarftMod {
    public static final String MODID = "tailcarft";

    public TailcarftMod(IEventBus modEventBus, ModContainer modContainer) {
        GameRuntime.init(new NeoForgeRuntimeEnv());

        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ServerMod.onStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> ServerMod.onStopping());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientMod.onInitializeClient();
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                    event -> ClientMod.onTick(event.getClient()));
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ScreenEvent.Opened.class,
                    event -> ClientMod.onScreenInit(event.getClient(), event.getScreen(),
                            event.getScreen().width, event.getScreen().height));
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientLifecycleEvent.CLIENT_STOPPING.class,
                    event -> ClientMod.onClientStopping(event.getClient()));
        }
    }
}
```

> Verify-by-build (NeoForge 1.21.1): the client event classes
> `ClientTickEvent.Post`, `ScreenEvent.Opened` (accessors `getClient()`,
> `getScreen()`), `ClientLifecycleEvent.CLIENT_STOPPING`, and the server events
> `ServerStartedEvent`/`ServerStoppingEvent` (accessor `getServer()`). If a class
> or accessor differs, use the NeoForge decompile-grep to copy the exact name.

- [ ] **Step 4: Build to green**

Run: `./gradlew :neoforge-1.21.1:build --console=plain`
Expected: `BUILD SUCCESSFUL`, producing `neoforge-1.21.1/build/libs/tailcarft-0.1.0-neoforge-1.21.1.jar`.
Fix any NeoForge/mojmap name with the decompile-grep helpers until green.

- [ ] **Step 5: Commit**

```bash
git add loader/neoforge
git commit -m "feat: neoforge 1.21.1 leaf"
```

---

### Task 10: Remaining NeoForge leaves (1.21.11, 26.1, 26.2, 26.3)

The version layers already exist (Tasks 3–8). Building each NeoForge leaf reuses
`common` + `loader/neoforge` + `version/<mc>`. The only shared NeoForge Java is
`TailcarftMod` (+ the stable `NeoForgeRuntimeEnv`); if a NeoForge version's event
API rejects the shared `TailcarftMod`, split it into per-version copies using the
`version/<mc>/src-neoforge/java` escape hatch.

**Files (per affected version):**
- Create (only if the shared `TailcarftMod` fails for that version):
  `version/<mc>/src-neoforge/java/com/tailscale/mclink/TailcarftMod.java`

**Interfaces:**
- Consumes: `common` + `loader/neoforge` + the six `version/<mc>` layers.
- Produces: green `:neoforge-1.21.11:build`, `:neoforge-26.1:build`,
  `:neoforge-26.2:build`, `:neoforge-26.3:build`.

- [ ] **Step 1: Build all four remaining NeoForge leaves**

Run: `./gradlew :neoforge-1.21.11:build :neoforge-26.1:build :neoforge-26.2:build :neoforge-26.3:build --console=plain`
Expected: `BUILD SUCCESSFUL` for all four.

- [ ] **Step 2: If a shared-class error appears, split `TailcarftMod` per version**

If the failure is in `loader/neoforge/.../TailcarftMod.java` (a NeoForge event
API that changed for that version), do the following for the failing version `<mc>`
(and any other version that then needs its own copy):

1. Move the class: `mkdir -p version/<mc>/src-neoforge/java/com/tailscale/mclink`
   then `mv loader/neoforge/src/main/java/com/tailscale/mclink/TailcarftMod.java version/<mc>/src-neoforge/java/com/tailscale/mclink/`.
2. After the first move, the other NeoForge versions lose the class — create a
   copy for each of them under their own `src-neoforge` root (adjusting each for
   its NeoForge event API via the decompile-grep helper).
3. The `mc-base` convention already adds `version/<mc>/src-neoforge/java` as a
   source root for the NeoForge leaf, so no build-file change is needed.

Re-run Step 1 until all four are green. Keep `NeoForgeRuntimeEnv` shared unless a
build proves it version-specific.

- [ ] **Step 3: Commit**

```bash
git add version loader/neoforge
git commit -m "feat: neoforge 1.21.11/26.1/26.2/26.3 leaves"
```

> **Note:** if a NeoForge version's `neoforge.mods.toml` needs a different
> `loaderVersion`, that is a per-leaf concern handled by `mc-base`'s property
> expansion only if it is parameterized; otherwise move the TOML into
> `version/<mc>/src-neoforge/resources/META-INF/` for that leaf.

---

### Task 11: Forge loader layer + `forge-1.20.1` green

Forge is 1.20.1-only. Adds the Forge `@Mod` entry, `RuntimeEnv`, and `mods.toml`,
reusing `common` + `version/1.20.1` (already green from Task 5). ForgeGradle is
already applied by `mc-forge`.

**Files (all new):**
- Create: `loader/forge/src/main/resources/META-INF/mods.toml`
- Create: `loader/forge/src/main/java/com/tailscale/mclink/ForgeRuntimeEnv.java`
- Create: `loader/forge/src/main/java/com/tailscale/mclink/TailcarftMod.java`

**Interfaces:**
- Consumes: `common` + `version/1.20.1`.
- Produces: `com.tailscale.mclink.TailcarftMod` (`@Mod("tailcarft")`, Forge 1.20.1),
  a green `:forge-1.20.1:build`.

> **Forge decompile-grep.** Same as the NeoForge helper but for the Forge 1.20.1
> jar (`find ~/.gradle/caches -name "forge-*-sources.jar"`). Forge 1.20.1 uses
> `net.minecraftforge.*` (not `net.neoforged.*`).

- [ ] **Step 1: Write `loader/forge/src/main/resources/META-INF/mods.toml`**

```toml
modLoader = "javafml"
loaderVersion = "[47,)"
license = "BSD-3-Clause"

[[mods]]
modId = "tailcarft"
version = "${version}"
displayName = "Tailcarft"
description = "Share Minecraft worlds through Tailcarft's userspace WireGuard transport."

[[mixins]]
config = "mclink.mixins.json"
```

> Confirm the Forge 1.20.1 `loaderVersion` (FML loader range) from a reference
> Forge 1.20.1 mod and adjust.

- [ ] **Step 2: Write `loader/forge/src/main/java/com/tailscale/mclink/ForgeRuntimeEnv.java`**

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ForgeRuntimeEnv implements RuntimeEnv {
    @Override public Path gameDir() { return FMLPaths.GAMEDIR.get(); }

    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }

    @Override public boolean isDevelopment() { return !net.minecraftforge.fml.loading.FMLEnvironment.production; }

    @Override public String modVersion() {
        return ModList.get().getModContainerById("tailcarft").orElseThrow()
                .getModInfo().getVersion();
    }
}
```

- [ ] **Step 3: Write `loader/forge/src/main/java/com/tailscale/mclink/TailcarftMod.java`**

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraftforge.Dist;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TailcarftMod.MODID)
public class TailcarftMod {
    public static final String MODID = "tailcarft";

    public TailcarftMod() {
        GameRuntime.init(new ForgeRuntimeEnv());
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
                .addListener((ServerStartedEvent event) -> ServerMod.onStarted(event.getServer()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
                .addListener((ServerStoppingEvent event) -> ServerMod.onStopping());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientMod.onInitializeClient();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS
                    .addListener(net.minecraftforge.client.event.ClientTickEvent.Post.class,
                            event -> ClientMod.onTick(event.getClient()));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS
                    .addListener(net.minecraftforge.client.event.ScreenOpenEvent.class,
                            event -> ClientMod.onScreenInit(event.getClient(), event.getScreen(),
                                    event.getScreen().width, event.getScreen().height));
        }
    }
}
```

> Forge 1.20.1 has no dedicated "client stopping" event in the same shape; if the
> build needs one, add the Forge 1.20.1 equivalent
> (`net.minecraftforge.client.event.ClientStoppedEvent` or the
> `ClientPlayerEvent`) via the Forge decompile-grep, wiring it to
> `ClientMod.onClientStopping`. If none exists, leave stopping to
> `ServerMod`/process exit and note it — the in-game Stop button already calls
> `ScreenState.close()`.

- [ ] **Step 4: Build to green**

Run: `./gradlew :forge-1.20.1:build --console=plain`
Expected: `BUILD SUCCESSFUL`, producing `forge-1.20.1/build/libs/tailcarft-0.1.0-forge-1.20.1.jar`.
Fix any Forge/mojmap name with the decompile-grep helpers until green.

- [ ] **Step 5: Commit**

```bash
git add loader/forge
git commit -m "feat: forge 1.20.1 leaf"
```

> **All 12 leaves build after Task 11.** Task 12 removes the old `mod/` tree and
> runs the full matrix + jar sanity.

---
### Task 12: Remove `mod/`, full matrix build, jar sanity

Deletes the old single-module tree, repoints the native-helper output path, runs
the whole 12-leaf build, and sanity-checks every jar.

**Files:**
- Delete: `mod/` (entire directory — now dead code)
- Modify: `scripts/build-natives.sh` (resource output path)
- Modify: `.gitignore` (drop `mod/`-specific lines, add the new native path)

**Interfaces:**
- Consumes: all 12 green leaves (Tasks 1–11).
- Produces: 12 jars `tailcarft-0.1.0-<loader>-<mc>.jar`, one per leaf.

- [ ] **Step 1: Repoint the native-helper resource path**

In `scripts/build-natives.sh`, change
`RES="$ROOT/mod/src/main/resources/assets/mclink/native"`
to
`RES="$ROOT/common/src/main/resources/assets/mclink/native"`.

- [ ] **Step 2: Update `.gitignore`**

Remove the `mod/`-specific lines (`mod/run/`, `mod/run-*/`,
`mod/src/main/resources/assets/mclink/native/`, `mod/bin`, `mod/logs/`).
Keep the generic `run/`, `**/build/`, and `local.properties`. Add:

```
# Generated helper natives (rebuilt by scripts/build-natives.sh)
common/src/main/resources/assets/mclink/native/
```

- [ ] **Step 3: Rebuild the helper natives into the new path**

Run: `bash scripts/build-natives.sh`
Expected: binaries land under `common/src/main/resources/assets/mclink/native/`.

- [ ] **Step 4: Delete the old module**

```bash
git rm -r mod
```

- [ ] **Step 5: Full clean build of all 12 leaves**

Run: `./gradlew clean build --console=plain`
Expected: `BUILD SUCCESSFUL`; 12 jars (plus 12 sources jars) across the leaf
`build/libs/` directories.

- [ ] **Step 6: Jar sanity check**

Run:
```bash
for d in fabric-1.20.1 fabric-1.21.1 fabric-1.21.11 fabric-26.1 fabric-26.2 fabric-26.3 \
         neoforge-1.21.1 neoforge-1.21.11 neoforge-26.1 neoforge-26.2 neoforge-26.3 forge-1.20.1; do
  jar=$(ls "$d/build/libs/tailcarft-"*.jar 2>/dev/null | grep -v sources | head -1)
  echo "== $d -> $jar"
  unzip -l "$jar" 2>/dev/null | grep -E "fabric.mod.json|neoforge.mods.toml|META-INF/mods.toml|mclink.mixins.json|native/linux-amd64/mclink-helper" || echo "  MISSING CONTENT"
done
```
Expected: every leaf lists its mod-metadata file, `mclink.mixins.json`, and at
least one native helper binary. No `MISSING CONTENT` lines.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: remove legacy mod module; build 12-leaf matrix"
```

---

### Task 13: CI workflow + README

Updates the release workflow to install all three JDKs, build all 12 leaves, and
stage 12 jars; updates the README for the multi-loader/multi-version matrix.

**Files:**
- Modify: `.forgejo/workflows/release.yaml`
- Modify: `README.md`

**Interfaces:**
- Consumes: the matrix build (Task 12).
- Produces: a release that publishes all 12 jars + `SHA256SUMS`.

- [ ] **Step 1: Rewrite `.forgejo/workflows/release.yaml`**

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

concurrency:
  cancel-in-progress: false

env:
  JDK17_HOME: /opt/jdk17
  JDK21_HOME: /opt/jdk21
  JDK25_HOME: /opt/jdk25

jobs:
  release:
    runs-on: ubuntu-latest
    timeout-minutes: 90
    steps:
      - name: Check out the repository
        uses: https://data.forgejo.org/actions/checkout@v6

      - name: Install Temurin JDK 17, 21, and 25
        run: |
          set -euxo pipefail
          for v in 17 21 25; do
            home="/opt/jdk$v"
            mkdir -p "$home"
            curl --retry 3 -fsSL -o "/tmp/jdk$v.tar.gz" \
              "https://api.adoptium.net/v3/binary/latest/$v/ga/linux/x64/jdk/hotspot/normal/eclipse"
            tar -xzf "/tmp/jdk$v.tar.gz" -C "$home" --strip-components=1
            rm -f "/tmp/jdk$v.tar.gz"
            "$home/bin/java" -version
          done

      - name: Build helper natives for all platforms
        run: |
          set -euxo pipefail
          bash scripts/build-natives.sh

      - name: Run Go tests
        run: |
          set -euxo pipefail
          GO=$(ls -d "${XDG_CACHE_HOME:-$HOME/.cache}/mclink/tsgo"/*/bin/go | head -n 1)
          cd helper
          GOFLAGS=-mod=mod CGO_ENABLED=0 "$GO" test ./...

      - name: Run Java tests and build all 12 leaves
        run: |
          set -euxo pipefail
          ./gradlew test build --console=plain --no-daemon \
            -Porg.gradle.java.installations.paths=/opt/jdk17,/opt/jdk21,/opt/jdk25

      - name: Verify the tag matches the mod version
        run: |
          set -euo pipefail
          version=$(sed -n 's/^mod_version=//p' gradle.properties)
          if [[ "v$version" != "${{ forgejo.ref_name }}" ]]; then
            echo "tag ${{ forgejo.ref_name }} does not match mod_version $version" >&2
            exit 1
          fi

      - name: Stage release assets
        run: |
          set -euxo pipefail
          mkdir -p dist/release
          for d in fabric-1.20.1 fabric-1.21.1 fabric-1.21.11 fabric-26.1 fabric-26.2 fabric-26.3 \
                   neoforge-1.21.1 neoforge-1.21.11 neoforge-26.1 neoforge-26.2 neoforge-26.3 forge-1.20.1; do
            cp "$d/build/libs/tailcarft-"*.jar dist/release/
          done
          (cd dist/release && sha256sum *.jar > SHA256SUMS)
          ls -l dist/release

      - name: Publish the Forgejo release
        uses: https://data.forgejo.org/actions/forgejo-release@v2.13.4
        with:
          direction: upload
          release-dir: dist/release
          title: ${{ forgejo.ref_name }}
          prerelease: ${{ contains(for}}ref_name, '-')}}
          override: 'true'
```

The only functional changes vs. the old workflow: three JDKs installed, the
Java step builds all leaves with the toolchain paths, staging copies 12 jars,
and the timeout is 90 minutes.

- [ ] **Step 2: Update `README.md`**

Make these edits (keep the existing voice):

- Intro: replace "A Fabric 1.21.1 client mod" with a multi-loader,
  multi-version description (Fabric, NeoForge, and Forge; the versions in
  `gradle.properties`).
- Add a **Supported versions** table mirroring the matrix (loader rows x
  version columns).
- **Build** section: "Requires JDK 21" becomes "Requires JDK 17, 21, and
  25". Replace `./gradlew :mod:build` with `./gradlew build` (all leaves) and
  a per-leaf example such as `./gradlew :neoforge-26.3:build`.
- **Releasing** section: jars are named `tailcarft-<mod_version>-<loader>-<mc>.jar`
  (12 per release); the workflow stages all 12 plus `SHA256SUMS`.

- [ ] **Step 3: Commit**

```bash
git add .forgejo README.md
git commit -m "ci: build and publish the 12-leaf matrix"
```

---

### Task 14: Convergence audit (optional cleanup)

After every leaf compiles, move any version-layer class that is byte-identical
across all versions back to `common`, leaving only genuinely drifted classes in
`version/<mc>`. This is a cleanup; do it only where files are identical, and
rebuild all 12 leaves afterward to confirm nothing broke.

**Files (conditional):** some or none of
`version/<mc>/src/main/java/com/tailscale/mclink/{ClientMod,ScreenState,ShareScreen,JoinRemoteScreen}.java`
and the `mixin/*.java` files, plus their `mclink.mixins.json` if the mixin list
is identical everywhere.

- [ ] **Step 1: Find byte-identical version classes**

```bash
for f in ClientMod ScreenState ShareScreen JoinRemoteScreen mixin/IntegratedServerAccessor mixin/ServerListAccessor mixin/ServerListMixin mixin/ConnectScreenMixin mixin/MultiplayerScreenMixin; do
  echo "== $f";
  for v in 1.20.1 1.21.1 1.21.11 26.1 26.2 26.3; do
    p="version/$v/src/main/java/com/tailscale/mclink/$f.java";
    [ -f "$p" ] && md5sum "$p";
  done;
done
```

For each file, if **every** present version has the **same** md5, it is a
convergence candidate. (Files that differ across versions stay per-version.)

- [ ] **Step 2: Move each candidate to `common` and delete the per-version copies**

For a candidate `F` present in versions `V1..Vn`:
```bash
mv version/1.21.1/src/main/java/com/tailscale/mclink/F.java common/src/main/java/com/tailscale/mclink/
for v in <other versions that had F>; do rm "version/$v/src/main/java/com/tailscale/mclink/F.java"; done
```
Repeat per candidate. Mixins that converge also need `mclink.mixins.json` moved
to `common/src/main/resources/` only if the config is identical for **all**
loaders and versions; otherwise keep it per-version (1.20.1 uses `JAVA_17`).

- [ ] **Step 3: Rebuild all 12 leaves**

Run: `./gradlew clean build --console=plain`
Expected: `BUILD SUCCESSFUL` for all 12 leaves (proves the common move is safe).

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: converge identical version classes into common"
```

If no class is byte-identical across all versions, this task is a no-op:
make no changes and skip the commit.
