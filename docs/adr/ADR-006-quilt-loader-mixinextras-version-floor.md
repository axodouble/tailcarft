# ADR-006: Quilt Loader version floor 0.30.2-beta.1 (MixinExtras 0.5.5)

**Date:** 2026-09-29
**Status:** Accepted

## Context

The Quilt leaves (1.20.1, 1.21.1) crashed at boot with a
`ClassCastException` in MixinExtras'
`FactoryRedirectWrapperMixinTransformer.transform` when processing any
`@Redirect` annotation on a mixin targeting `Minecraft`. The crash occurred
during the `preApply` phase, before any of our mixins were applied, making all
Quilt leaves unbootable.

Root cause: Quilt Loader bundles MixinExtras as an embedded mod inside the
quilt-loader JAR (`META-INF/jars/mixinextras-fabric-*.jar`). The bundled
versions up to and including MixinExtras 0.5.4 (Quilt Loader 0.30.1) use
`Annotations.getValue(annotation, "at")` which returns the raw value and then
do a direct `checkcast AnnotationNode`. When the `@Redirect` annotation's `at`
field is stored as an `ArrayList` (an array of `@At` annotations, which is the
normal case for `@Redirect`), the cast fails with:

```
ClassCastException: class java.util.ArrayList cannot be cast to
    class org.objectweb.asm.tree.AnnotationNode
```

MixinExtras 0.5.5 (first bundled in Quilt Loader 0.30.2-beta.1) fixes this by
using the list-returning overload `Annotations.getValue(annotation, "at", true)`
and iterating the result, handling both single-annotation and array-of-annotations
storage correctly.

The Quilt Loader versions and their bundled MixinExtras:

| Quilt Loader | MixinExtras | Fixed? |
|---|---|---|
| 0.26.4 | 0.4.1 | No |
| 0.30.1 (stable) | 0.5.4 | No |
| 0.30.2-beta.1 | 0.5.5 | Yes |
| 0.31.0-beta.4 | 0.5.5 | Yes |

## Decision

Set the Quilt Loader version for all Quilt leaves (1.20.1 and 1.21.1) to
`0.30.2-beta.1`, the earliest release that bundles the fixed MixinExtras 0.5.5.
Quilt Loom is upgraded to `1.15.1` to match.

The Quilt loader metadata uses `fabric.mod.json` (not `quilt.mod.json`) because
`QuiltMixinBootstrap.getMixinConfigs()` only registers Mixin configs for
`FabricLoaderModMetadata` instances; a `V1ModMetadata` (from `quilt.mod.json`)
would silently skip all Mixin registration.

## Consequences

- The Quilt leaves depend on a beta Quilt Loader release. If a stable
  0.30.2 or 0.31.0 release with MixinExtras >= 0.5.5 becomes available, the
  version should be bumped to the stable release.
- The Quilt leaves use `fabric.mod.json` for mod metadata, which is supported
  by Quilt Loader (it is a Fabric-Loader fork) but means the Quilt-specific
  `quilt.mod.json` features (e.g. `intermediate_mappings`) are not available.
  This is acceptable: the default `intermediate_mappings` value
  (`net.fabricmc:intermediary`) is what we want.
- The dev environment requires a **named** (unremapped) JAR in `run/mods/`
  because Quilt Loader 0.30.2-beta.1 does not remap intermediary JARs at
  runtime in the dev environment. The `remapJar` task output (intermediary)
  must not be placed in `run/mods/` for dev runs.

## Related

- **Issues:** #31 (Mixin engine missing at runtime), #32 (MixinExtras crash)
- **ADRs:** ADR-003 (loader/version matrix), ADR-005 (tick-hook design)
