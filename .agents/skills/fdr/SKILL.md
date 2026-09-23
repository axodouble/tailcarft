---
name: fdr
description: "Use when a feature is designed, built, changed, or removed — record or update its Feature Decision Record, or consult how a feature works and why. Triggers: feature behavior, design rationale, permission gating, feature docs, mentions of FDRs or docs/fdr. Cross-cutting architecture choices belong to the adr skill; auditing FDRs belongs to fdr-review."
---

# Feature Decision Records (FDRs)

Manage features as structured markdown documents in `docs/fdr/`. Each FDR captures what a feature does from a user perspective **and** the design decisions that shaped it, with rationale.

## What an FDR Is

An FDR is a single source of truth for one feature. It answers:

- **What** does the feature do, behaviorally? (no implementation details)
- **Why** is it built this way? (the design decisions, with rationale)
- **Where** does it fit? (related ADRs, related FDRs, permissions that gate it)

## Alongside superpowers

Feature decisions land mid-flow: `superpowers:brainstorming` gets one accepted; a subagent that departs from the plan has made one. Create or update the FDR the moment it lands, then continue. Before designing in an area — and when briefing a subagent into it — read `docs/fdr/INDEX.md` and the records it names: current FDRs constrain the redesign and carry the *why* that code can't.

FDRs sit alongside ADRs (`docs/adr/`). The split:

- **ADRs** are about **architectural decisions** — cross-cutting choices like "protobuf-first public API" or "per-user encryption keys". Often immutable once decided.
- **FDRs** are about **features** — what they do and the design decisions specific to that feature. Updated as the feature evolves.

A single feature may cite several ADRs; a single ADR may underpin several FDRs. That's fine and expected.

## What an FDR Is NOT

- **NOT** a code walkthrough. No function signatures, no schema or wire-format dumps, no storage key patterns.
- **NOT** a file index. No "Key Files" tables.
- **NOT** an implementation guide. Agents can `grep` for those things.
- **NOT** a changelog. Superseded design decisions get rewritten, not appended. The FDR describes the feature *today*.

If you find yourself writing schema definitions, handler internals, or code in an FDR, you're going too deep. Stop and pull back to behavior + rationale.

## Directory Structure

```
docs/fdr/
├── INDEX.md                                # Index with TOC (read this first)
├── FDR-001-roles-and-permissions.md       # Individual FDR
├── FDR-002-replies-and-threads.md
└── ...
```

If `docs/fdr/` does not exist yet, create it with an `INDEX.md` carrying the TOC header below before writing the first FDR.

## File Naming

```
FDR-{NNN}-{kebab-case-slug}.md
```

- `NNN`: Zero-padded three-digit number, sequential
- Slug: Short kebab-case summary (not the full title)

## FDR Template

```markdown
# FDR-{NNN}: {Feature Name}

**Status:** Active
**Last reviewed:** {YYYY-MM-DD}

## Overview

One paragraph: what the feature is, who uses it, and why it exists.

## Behavior

Bullet points describing user-visible behavior. No implementation details.

## Design Decisions

Numbered list. Each entry calls out a non-obvious choice and *why*.

### 1. {Short decision title}

**Decision:** What we chose.
**Why:** The reasoning. Cite an ADR if there is one.
**Tradeoff:** What this costs us.

## Permissions

Permission strings that gate this feature, with one-line descriptions.
Omit this section for features that aren't permission-gated.

## Related

- **ADRs:** ADR-XYZ, ADR-ABC
- **FDRs:** FDR-NNN

## Open Questions

Optional. Known design gaps, future considerations, or things we
deliberately haven't decided yet. Delete this section if there's
nothing to say.
```

### Status values

- **Active** — feature is in the codebase and supported
- **Experimental** — feature is in the codebase but unstable
- **Retired** — feature was removed but the FDR is kept to document the prior design (rare; usually we delete instead)

## Workflow

### Before doing anything

1. Read `docs/fdr/INDEX.md` to see the current list of FDRs
2. Only read individual FDR files if relevant to the current task

### Creating a new FDR

1. Read `docs/fdr/INDEX.md` to determine the next available number
2. Confirm the slug, number, and scope with the user before writing
3. Use the template above; fill in every required section
4. Set **Last reviewed** to today's date
5. Add the new entry to `docs/fdr/INDEX.md`
6. Cross-reference: if the FDR cites an ADR, the ADR doesn't need updating — citations flow one direction (FDR → ADR)
7. **Sibling check**: for each ADR you cite, skim other FDRs that already cite it. They're likely related to yours and worth listing in your `Related → FDRs` line.

### Updating an FDR

1. Read the FDR
2. Make targeted edits. Don't append "Update: ..." notes — rewrite the affected section so the doc describes the feature *today*.
3. Bump **Last reviewed** to today's date
4. If the title changed, update `docs/fdr/INDEX.md`

### Retiring an FDR

When a feature is removed:

- **Default:** Delete the FDR. Remove the entry from `INDEX.md`. The git history preserves it if anyone needs to look back.
- **Exception:** Set status to `Retired` and keep it only if the prior design is notable and likely to inform future work (e.g., a system that was deliberately rolled back and might be reconsidered). Add a top-level note explaining why it was retired.

## Writing Style

- **Behavior section: bullet points, short paragraphs.** No code blocks. Describe what users see and experience.
- **Design Decisions: numbered, with explicit Why + Tradeoff.** Each entry should be defensible to a future maintainer who didn't live through the original conversation.
- **Mention permission strings** — they're part of the feature design. But don't describe *how* permission checks are implemented.
- **Cite ADRs by number** when a design decision is downstream of an architectural choice. Don't restate the ADR; just point to it.
- **Omit sections that don't add value.** A simple feature might just need Overview + Behavior + Permissions. Don't pad.

## Auditing

To verify FDRs against the codebase — behavior drift, dead references, renamed permissions — use the `fdr-review` skill.

## TOC Format (in INDEX.md)

```markdown
| # | Feature | Status | Last reviewed |
|---|---------|--------|---------------|
| [FDR-001](FDR-001-slug.md) | Title of the feature | Active | 2026-05-19 |
```
