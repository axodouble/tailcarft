---
name: adr-review
description: "Use when the user asks for an ADR review, ADR audit, stale ADR check, or architecture decision consistency check, when a superpowers plan run or large merge may have left decisions unrecorded, or when ADRs need verifying against the current codebase, feature/design records, architecture docs, glossary, or public API state. Audits all of docs/adr for implementation drift, contradictions, stale or superseded decisions, weak rationale, and missing cross-references. Feature-record audits belong to the fdr-review skill."
---

# ADR Review

Review ADRs as architectural records that must remain useful to future maintainers. An ADR review is an audit and recommendation pass, not a rewrite pass unless the user explicitly asks for edits.

## Ground Rules

- Read `docs/adr/INDEX.md` first.
- Audit the full ADR set: list `docs/adr/` so every file is accounted for — the directory listing, not the index alone, is the inventory.
- Read individual ADR files according to the full-audit plan; read related ADRs together when they form a decision chain.
- Report findings and concrete proposed edits; apply edits only when the user asks.
- Use current code and docs as evidence. A newer preference alone does not make an ADR stale — report the contradiction, missing supersession, or drift it reveals.
- Run the review at the points drift is born, not only on request: the end of a superpowers plan run or after a large merge, where a decision may have been made in the flow and never written down.
- If editing is requested, also follow the `adr` skill's update/supersede workflow.

## Full ADR Audit

1. Read `docs/adr/INDEX.md`, then list `docs/adr/` for the complete inventory.
2. Group ADRs by topic so related decisions are checked together.
3. Prioritize recent ADRs, superseded/superseding chains, and ADRs likely to affect current architecture:
   - storage, data models, events, jobs, queues, caches, indexes, and projections
   - public APIs, wire protocols, compatibility, and generated clients
   - authorization, identity, tenancy, privacy, and encryption
   - frontend/client architecture, localization, delivery, and deployment
4. Audit in parallel with subagents when available; otherwise use local searches and read-only shell commands.
5. Write a consolidated report rather than scattering findings across ADRs.
6. Search for related feature records, design records, architecture docs, and user-facing docs that cite or should cite each ADR.
7. Check the current implementation and relevant docs for every ADR topic.

## Evidence Sources

Read [EVIDENCE.md](EVIDENCE.md) — the evidence taxonomy shared with `fdr-review`; install the two review skills together.

## What To Check

For every ADR, check:

- **Existence and index:** file exists; the index row points to the right file and matches title, status, and date.
- **Decision clarity:** the Decision section states a concrete architectural choice, not only intent or background.
- **Context currency:** the motivating problem still makes sense or is clearly historical.
- **Implementation drift:** current code, schemas, generated APIs, runtime resources, deployment config, or client structure contradict the ADR.
- **Supersession:** the **Status** line carries supersession (`Superseded by ADR-NNN`), the replacement ADR's Context names the old decision, and the TOC status column makes it discoverable.
- **Consequences:** consequences name real tradeoffs, migration costs, compatibility constraints, operational effects, or maintenance burdens.
- **Public compatibility:** storage, schemas, discovery, API, protocol, and mixed-version implications are explicit when relevant.
- **Cross-references:** related feature/design records, architecture docs, and user-facing docs cite or align with the ADR where appropriate.
- **Vocabulary:** terms match the project's glossary, domain model, and current product or architecture naming.
- **Documentation drift:** architecture docs, docs website pages, related decision records, and feature docs do not describe a different current architecture.

## Finding Categories

Classify findings with one of these labels:

- **Contradiction:** the ADR conflicts with current code or another active ADR.
- **Stale:** the ADR describes old architecture without a supersession/update note.
- **Missing Supersession:** a newer ADR or implementation replaced the decision, but the old ADR's Status does not say so.
- **Weak Decision:** the ADR does not record a concrete choice.
- **Weak Consequences:** important tradeoffs, compatibility impact, or operational costs are omitted.
- **Missing Cross-Reference:** related records or docs should cite or align with the ADR.
- **Index Issue:** `docs/adr/INDEX.md` is missing, mislabeling, or mislinking an ADR.
- **No Issue:** checked and no material update is needed.

## Report Format

Start with findings, ordered by severity. Use file links and concrete evidence.

```markdown
## Findings

- **Contradiction:** [ADR-042](docs/adr/ADR-042-example-decision.md) says ...
  Evidence: `src/...` now ...
  Proposed fix: ...

## Clean / Low-Risk ADRs

- ADR-044: checked against the relevant implementation and docs; no material drift found.

## Open Questions

- ...

## Proposed Edits

- Update ADR-...
- Set Status of ADR-... to `Superseded by ADR-...`
- Update related feature/design record references.
```

Keep the report concise. For large audits, include a summary table and save per-ADR detail in the project's scratch or notes location (`.context/adr-review-YYYY-MM-DD.md` where that convention exists) when it helps later collaboration.

## Applying Fixes

Only apply fixes when the user asks.

When applying fixes:

1. Use the `adr` skill workflow for updating or superseding ADRs.
2. Preserve ADR numbering and filenames unless creating a new ADR.
3. Update `docs/adr/INDEX.md` if a title or status changed, or a new ADR was added.
4. Run a cross-reference sweep: scan `docs/fdr/INDEX.md` and other related record indexes, and update ADR reference lines when an ADR becomes relevant.
5. Update architecture docs, glossary/terminology docs, or docs website pages only when a finding requires it and the user approved edits.
6. Run targeted verification for edited docs, such as markdown link checks or focused grep checks for references.
