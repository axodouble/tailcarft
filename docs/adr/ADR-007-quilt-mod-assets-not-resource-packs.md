# ADR-007: Quilt mod assets are not resource packs — merge the mod's lang into a fresh ClientLanguage from the Minecraft tick hook

**Date:** 2026-10-03
**Status:** Accepted

## Context

On the Quilt leaves (1.20.1, 1.21.1) the "Connect with Tailcarft" button
renders with the raw key `mclink.join` as its label instead of the translated
text. On Fabric and Forge the identical lang file
(`assets/mclink/lang/en_us.json`) translates correctly, so the problem is
Quilt-specific, not a bad lang file.

Two independent obstacles block the usual paths for delivering the mod's
translations:

1. **Quilt does not register the mod's `assets/` directory as a resource
   pack.** Fabric and Forge expose a mod's `assets/` tree to the vanilla
   `ResourceManager`, which is how `Language` discovers and loads mod lang
   files. Under Quilt the resource manager reports only the `vanilla` pack;
   the mod's `assets/mclink/lang/*.json` are invisible to the translation
   loader. Quilt's resource manager only auto-registers packs discovered
   through the loader's mod/asset pipeline, which for our jar does not expose
   `assets/` as a pack.

2. **The vanilla `ClientLanguage` is loaded before a Mixin can reach it.**
   `ClientLanguage` is the `Language` instance that holds the loaded
   translation map. It is constructed and populated during resource init,
   before the Mixin agent transforms it, so a `@Mixin(ClientLanguage.class)`
   is never woven. This was confirmed directly: diagnostic `@Inject` hooks on
   `ClientLanguage.loadFrom` and `ClientLanguage.appendFrom` never fire under
   Quilt, whereas the equivalent `Minecraft` hook fires every tick.

So the mod's lang cannot be delivered by (a) the resource-pack path or (b) a
mixin on `ClientLanguage`. A Quilt-API resource-pack registration would be the
"proper" fix, but the Quilt API is not a dependency of the leaf (only
`quilt-loader`, `quilt-json5`, `quilt-config` are), so there is no API surface
available to register a pack.

## Decision

Deliver the mod's lang from the always-woven `Minecraft` tick hook — the same
early, always-woven class that ADR-005 uses to add the button — but in a
Quilt-only mixin, `ModLanguageMixin` (in the quilt loader source set). On the
first tick, guarded by a `@Unique` boolean so it runs exactly once, the hook:

1. Reads the current `ClientLanguage`'s translation map by **type-based
   reflection**: it finds the single `Map`-typed declared field on
   `ClientLanguage` and copies its entries.
2. Merges the mod's `en_us.json` into that copy with `Language.loadFromJson`.
3. Builds a **fresh** `ClientLanguage` from the merged map via its private
   constructor, resolved **by descriptor**
   (`getDeclaredConstructor(Map.class, boolean.class)`), and installs it with
   `Language.inject(...)`.

Reflection is resolved by field type and constructor descriptor rather than by
member name, so it is independent of whichever intermediary mapping is active
at runtime (Quilt uses `net.fabricmc:intermediary` by default, per ADR-006).
The whole body is wrapped in a catch-all so a failure degrades to the raw key
rather than crashing the client over a translation.

## Consequences

- The translation is applied one tick after resources load. This is
  imperceptible: the button is added on first appearance of the multiplayer
  screen (also from the tick hook, ADR-005), and by then the map is stable.
- The hook reads a `Map` field reflectively and **replaces the whole
  `Language` instance** instead of mutating the existing map in place. This
  avoids writing a `final` field and is mapping-independent, but it encodes a
  version-coupled assumption: that `ClientLanguage` exposes exactly one
  `Map`-typed field and a `(Map, boolean)` constructor. If a future Minecraft
  version changes either, the hook must be updated; until then it silently
  degrades to the raw key (the catch-all swallows the failure).
- The mixin lives only in the quilt loader source set, so Fabric and Forge are
  unaffected — they deliver the same lang through their normal resource-pack
  path.
- Verified by `scripts/gui-smoke.sh` (ADR-004) plus a screenshot read: on
  Quilt 1.20.1 and 1.21.1 the button label renders as "Connect with Tailcarft"
  (the translated value) rather than `mclink.join`.

## Related

- **Issues:** #34 (Quilt leaves show the raw `mclink.join` key, not the
  translated label)
- **ADRs:** ADR-005 (Minecraft tick-hook mechanism), ADR-006 (Quilt loader
  version floor, `fabric.mod.json`, and the named dev-jar constraint),
  ADR-004 (headless GUI smoke test)
