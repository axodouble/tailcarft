# ADR-002: Verify mixins statically with ASM in the leaf unit tests

**Date:** 2026-09-23
**Status:** Accepted

## Context

The 0.2.6 beta line shipped, across a handful of builds, five distinct
classes of mixin defects, each of which only manifests when a client actually
starts: a non-mixin helper class placed in the guarded mixin package
(#15), an injection targeting a method removed in a newer Minecraft
(#16), an unmatchable callback parameter on a zero-argument target (#17), an
`@Inject` callback missing its `CallbackInfo` parameter (#26), and a TAIL
injection that silently bound to an exception-handler or guard-clause return
(ADR-001). None of them produced a build error; each required a manual game
session on the affected loader to surface.

The mod builds six leaf jars (Fabric/NeoForge/Forge across 1.20.1, 1.21.1,
26.3), so a manual matrix of game sessions is not a sustainable verification
strategy.

## Decision

`MixinRegressionTest` runs in the `test` task of every leaf and parses the
compiled mixin classes *and* their named Minecraft target classes straight
off the leaf test classpath with ASM — no Minecraft runtime, no launcher, no
mocks. It currently rejects:

1. classes in the declared mixin package that are not themselves `@Mixin`
   (the loader package guard fails them at load time);
2. injection targets whose named method does not exist on the target
   hierarchy;
3. zero-argument injection callbacks that declare an extra
   (unmatchable) parameter;
4. `@Inject` callbacks that do not end with a `CallbackInfo` or
   `CallbackInfoReturnable` parameter;
5. `@At("TAIL")` injections whose target's last return in bytecode order is
   not where the method's longest normal (non-exception) path from the
   entry ends — the layout that makes TAIL bind to an early-exit or
   exception-handler return (ADR-001).

Check 5 walks the target's instruction list with a bounded
longest-path relaxation over normal control-flow edges (exception edges
excluded) and compares the return the body actually ends on with the last
return in bytecode order.

## Consequences

- Mixin defects that were previously runtime crashes (or silent no-ops) are
  build failures on every leaf, on every push.
- The tests are only as strong as the leaf test classpath: they verify
  against the exact Minecraft (and, for NeoForge leaves, NeoForge-patched)
  jar that the jar ships against, including whether names are official or
  intermediary for that version. A classpath change that swaps in a
  differently-patched jar silently changes what is verified.
- The bytecode heuristics are approximations of Mixin's own resolution.
  Check 5's longest-path rule is deliberately conservative: it can flag a
  TAIL that Mixin would apply (the hook still works, but the build asks for
  a deliberate RETURN conversion), and it can miss a layout it has not seen.
  When a new false positive or negative appears, extend the test and note
  the layout here rather than weakening the check.
- The test depends on the private shape of mixin annotations as stored in
  class files (e.g. `@Inject.at` is an `At[]` array element); a Mixin or
  compiler change that alters that shape breaks the test parser first,
  which is the desired failure mode.
