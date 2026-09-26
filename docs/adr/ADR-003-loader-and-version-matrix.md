# ADR-003: Loader and Minecraft version matrix

**Date:** 2026-09-25
**Status:** Accepted

## Context

Tailcarft ships one jar per (loader, Minecraft version) combination; the
release workflow and README are driven by the `targets.<loader>` properties.
The loader matrix was requested to grow from Fabric (1.20.1, 1.21.1, 26.3),
NeoForge (1.21.1, 26.3), and Forge (1.20.1) to also cover Forge, Quilt, and
LiteLoader at 1.20.1, 1.21.1, and 26.3 — only where feasible.

Feasibility was checked against upstream on 2026-09-25:

- Forge publishes for all three versions (1.20.1-47.4.23, 1.21.1-52.1.16,
  26.3-66.0.3) and ForgeGradle 7.0.40 is current.
- Quilt (Quilt Loom, quilt-loader) publishes for 1.20.1 and 1.21.1 but has
  no release for the 26.x line.
- LiteLoader never supports these versions: LiteLoader 1 stops at 1.12.2 and
  the LiteLoader 2 project is defunct with no artifacts for any target
  version. Its modding API is also unrelated to the Forge/Fabric-style entry
  points the rest of the codebase is built around.

## Decision

- The matrix follows upstream loader availability: a leaf is built only when
  the loader exists for that Minecraft version. The current matrix is Fabric
  (1.20.1, 1.21.1, 26.3), NeoForge (1.21.1, 26.3), Forge (1.20.1, 1.21.1,
  26.3), and Quilt (1.20.1, 1.21.1).
- LiteLoader is excluded entirely, and Quilt is excluded for 26.3, for the
  reasons above. Either can be re-added with a `gradle.properties` change if
  the upstream situation changes.
- The Quilt loader is wired without Quilt Base: the loader-specific behavior
  (screen-init and client-shutdown hooks, server lifecycle for hosting) is
  provided by small mixins, matching how the Forge and NeoForge loaders get
  it, rather than depending on a loader API the other loaders don't have.

## Consequences

- The release publishes ten jars instead of six; the workflow loop and
  README must track the `targets.*` properties.
- The Quilt loader shares the common and per-version code with the other
  loaders; anything Quilt-specific and version-specific lives in the existing
  `version/<mc>/src-quilt/` escape hatch.
- Forge 1.21.1 and 26.3 reuse the existing Forge loader sources; if the newer
  Forge APIs drift, the same escape-hatch mechanism applies.
- Quilt Loom (a fork of Fabric Loom) does not place the Mixin API on the
  compile classpath the way Fabric Loom does. The quilt leaves therefore
  declare the Mixin API explicitly as a compile/test-only dependency
  (`spongemixin` in `gradle.properties`) and keep it off the runtime classpath
  so it is not bundled into the jar; quilt-loader supplies the Mixin engine at
  runtime. If Quilt Loom ever injects it itself, the explicit dependency can be
  dropped.
