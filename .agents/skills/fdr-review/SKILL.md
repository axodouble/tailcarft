---
name: fdr-review
description: "Use when the user asks for an FDR review, feature audit, stale feature-doc check, or feature/design-record consistency check, when a superpowers plan run or large merge may have left feature code undocumented, or when Feature Decision Records need verifying against the current codebase, permissions, architecture docs, or ADRs."
---

# FDR Review

Review Feature Decision Records so each one describes its feature *today*: behavior, design rationale, and references that match the codebase. Propose-only by default: report findings and concrete proposed edits; do not modify records or docs unless the user asks.

## Ground Rules

- Read `docs/fdr/INDEX.md` first, then audit the full FDR set — do not decide scope from a partial file list.
- Read `docs/fdr/` files according to the audit plan; read related FDRs together when they describe one feature area.
- Where code and FDR disagree, report the direction of the drift (stale record vs. code that departed from the recorded design) — do not silently pick a winner.
- Use current code and docs as evidence, not newer preferences.
- Run the audit at the points drift is born, not only on request: the end of a superpowers plan run or after a large merge, where implementation may have departed from the recorded design.

## Evidence Sources

Read [../adr-review/EVIDENCE.md](../adr-review/EVIDENCE.md) — the shared evidence taxonomy. If that skill is not installed, use the subset relevant to features: FDR inventory, architecture docs, glossary, permission definitions, public surface, core implementation, client implementation.

## What To Check

For every FDR, check:

- **Existence and index:** file exists; `INDEX.md` row points to it with matching title, status, and last-reviewed date.
- **Behavior currency:** each user-visible claim matches what the code does; significant user-facing behavior the FDR omits.
- **Design decisions:** stated rationales still hold; implementation drifted from a recorded decision.
- **Permissions:** cited permission strings exist in the codebase's permission definitions and are gated where the FDR claims.
- **References:** cited ADRs and FDRs exist, are still relevant, and carry no `Related` entries pointing at deleted records.
- **Depth:** implementation detail that belongs in code (schema dumps, signatures, file indexes) has crept in — flag for removal.
- **Vocabulary:** terms match the project's glossary and current product naming.

## Audit Process

When auditing more than one FDR, dispatch one read-only subagent per FDR. Give each subagent: the FDR path, the evidence-source list, and this prompt shape —

> Read {path} in full. Verify each behavioral claim, permission string, design-decision rationale, and cited ADR/FDR against the codebase. Return: **Verified** (claims confirmed, with file evidence), **Discrepancies** (claims contradicting code, both sides quoted), **Stale** (no longer applies), **Missing** (user-facing behavior undocumented). Cite file paths.

If subagents are unavailable, audit sequentially with local searches and read-only commands.

## Finding Categories

- **Contradiction:** the FDR conflicts with current code or another active record.
- **Stale:** behavior or rationale no longer applies.
- **Missing:** significant user-facing behavior the FDR omits.
- **Dead reference:** cites a record that was deleted or superseded.
- **Depth violation:** implementation detail that should be pulled back to behavior + rationale.
- **Index Issue:** `INDEX.md` missing, mislabeling, or mislinking an FDR.
- **No Issue:** checked, no material update.

## Report Format

```markdown
## Findings

- **Stale:** [FDR-007](docs/fdr/FDR-007-example.md) says reactions notify all thread
  participants. Evidence: `notifyOnReaction` now only fires on first reaction per user
  (`src/.../notify.go:88`). Proposed edit: rewrite the Behavior bullet.

## Clean / Low-Risk FDRs

- FDR-009: verified against implementation; no drift.

## Proposed Edits

- Rewrite FDR-007 Behavior bullet ...
```

Keep the report concise. For large audits, lead with a summary table (FDR, category, severity) and save per-FDR detail in `.context/fdr-review-YYYY-MM-DD.md` when it helps later collaboration.

## Applying Fixes

Only when the user asks, then follow the `fdr` skill's update/retire workflow: rewrite the affected sections so the FDR describes the feature today, bump **Last reviewed**, and sync `docs/fdr/INDEX.md`. Finish with targeted verification — markdown link check, plus grep that removed permission strings and dead references are gone.
