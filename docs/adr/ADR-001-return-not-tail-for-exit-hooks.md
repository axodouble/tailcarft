# ADR-001: Use @At("RETURN") with a return-value check for method-exit hooks

**Date:** 2026-09-23
**Status:** Accepted

## Context

The mod hooks `IntegratedServer.publishServer` / `unpublishServer` to notice
when a world is opened to (or closed from) LAN, then starts or stops the
bundled helper and announces the invite in chat.

The hooks were written with `@At("TAIL")`, which Mixin resolves to the last
return instruction in the target method's bytecode order. That is not
reliably "the end of the method":

- In the vanilla bytecode of every supported version (1.20.1, 1.21.1, 26.3),
  the success `return true` of `publishServer` is emitted before the
  `IOException` handler's `return false`, so TAIL bound to the handler return
  and the hook never fired on a successful publish.
- In the NeoForge-patched 26.3 bytecode, `publishServer(MultiplayerScope, int)`
  additionally ends in a guard clause's `return false`
  (`scope == OFF || isPublished()`) that the compiler places after the main
  body's return, so TAIL bound there instead: the hook fired only when
  publishing was skipped, never when it succeeded.

Both layouts produce no build error and no runtime error — the injection
applies cleanly and the client runs; the hook is simply dead. The defect was
only visible as "the invite is never announced", on Fabric (silent) and
NeoForge (unhelpful log noise) alike.

## Decision

Hooks that must fire when a method *completes its work* use
`@Inject(at = @At("RETURN"))` and inspect
`CallbackInfoReturnable.getReturnValueI()` (or the typed equivalent) to act
only on the outcome they care about. `@At("TAIL")` is reserved for targets
whose last return in bytecode order is provably the body's terminal return,
which `MixinRegressionTest.tailInjectionsStayOnTheNormalControlFlow`
verifies at build time (see ADR-002) for every TAIL injection in the codebase.

## Consequences

- A RETURN callback runs at every return site of the target, including early
  exits and handler returns; each callback must filter on the return value.
  This is a small cost paid at every exit instead of a silent no-op on the
  main path.
- The per-version method signatures of the hooked methods must be kept
  exact (the 1.20.1/1.21.1 descriptors had a `(GameType;ZZ)Z` typo for the
  real `(GameType;ZI)Z`); the same regression test rejects a descriptor that
  matches nothing on the test classpath.
- `unpublishServer` keeps TAIL on 26.3: it has a single terminal return and
  the regression test passes it. If a future Minecraft or loader patch
  changes that layout, the test fails at build time and the hook is
  converted to RETURN the same way.
- Any new exit hook added to this codebase should be added as RETURN first;
  switching one to TAIL requires the regression test's longest-normal-path
  check to pass for its target.
