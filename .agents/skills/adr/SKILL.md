---
name: adr
description: "Use when any architecture or technical decision is made, proposed, changed, or superseded — recording it, retrieving it, or checking it. Triggers: stack, storage, data models, protocols, public APIs, security, deployment choices; mentions of ADRs, docs/adr, or an architecture decision log. Per-feature behavior and design belong to the fdr skill."
---

# Architecture Decision Records (ADRs)

Manage architectural decisions as structured markdown documents in `docs/adr/`.

## Alongside superpowers

Decisions land mid-flow: `superpowers:brainstorming` gets one accepted, an execution task uncovers one. Write the ADR the moment the choice is accepted, then continue the task. Before designing in an area — and before briefing a subagent into it — read `docs/adr/INDEX.md` and the records it names: accepted ADRs are constraints on the options, and they carry the *why* the next agent inherits.

## Directory Structure

```
docs/adr/
├── INDEX.md                                          # Index with TOC (read this first)
├── ADR-001-nats-jetstream-instead-of-kafka.md      # Individual ADR
├── ADR-002-embedded-nats-server.md
└── ...
```

If `docs/adr/` does not exist yet, create it with an `INDEX.md` carrying the TOC header below before writing the first ADR. Every project has decisions worth logging; the first one seeds the log.

## Workflow

### Before doing anything

1. Read `docs/adr/INDEX.md` to see the current index of all ADRs
2. Only read individual ADR files if their content is relevant to the current task

### Creating a new ADR

1. Read `docs/adr/INDEX.md` to determine the next available number
2. Create the ADR file using the template below
3. Update `docs/adr/INDEX.md` to add the new entry to the TOC
4. **FDR sweep** — only when `docs/fdr/INDEX.md` exists: scan it for features whose design now relates to this ADR, and update those FDRs to cite the new ADR in their `Related → ADRs` line. Citations flow FDR → ADR only; ADRs themselves carry no `Related FDRs` section.

### Updating an ADR

1. Read `docs/adr/INDEX.md` to find the ADR
2. Read the specific ADR file
3. Make changes (typically amending the consequences or adding context)
4. Update the TOC in `docs/adr/INDEX.md` if the title changed

### Superseding an ADR

1. Create a new ADR that references the old one in its Context
2. Set the old ADR's **Status** to `Superseded by ADR-{NNN}`
3. Update the TOC so both entries carry their status

## File Naming

```
ADR-{NNN}-{kebab-case-slug}.md
```

- `NNN`: Zero-padded three-digit number, sequential
- Slug: Short kebab-case summary of the decision (not the full title)

## ADR Template

```markdown
# ADR-{NNN}: {Title}

**Date:** {YYYY-MM-DD}
**Status:** Accepted

## Context

What is the issue that we're seeing that is motivating this decision or change?

## Decision

What is the change that we're proposing and/or doing?

## Consequences

What becomes easier or more difficult to do because of this change?
```

### Status values

- **Accepted** — the decision is in force (default)
- **Proposed** — under discussion, not yet decided
- **Deprecated** — no longer encouraged, no replacement recorded
- **Superseded by ADR-{NNN}** — replaced; the newer ADR explains what changed

## TOC Format (in INDEX.md)

Each entry in the TOC should be a markdown table row:

```markdown
| # | Decision | Status | Date |
|---|----------|--------|------|
| [ADR-001](ADR-001-slug.md) | Title of the decision | Accepted | 2026-03-01 |
```
