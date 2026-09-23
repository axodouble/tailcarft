---
name: decision-records
description: "Use whenever a decision, choice, or design direction is made, proposed, weighed, or changed while working on a project — tech stack, architecture, data, protocols, feature behavior, or process. Works alongside superpowers process skills (brainstorming, writing-plans, executing-plans, subagent dispatch): those flows are where decisions happen, and each phase both reads and writes the record log. Also use when asked where decisions are recorded or how to audit them. Routes to the adr, fdr, adr-review, and fdr-review skills."
---

# Decision Records

A decision is not made until a future maintainer can read it. Log it before the task continues — the record is part of the work, not an afterthought.

## The log is the agent understanding

Code and plans show *what* was built; only the record carries *why*. A fresh agent, or a subagent about to be dispatched, that reads `docs/adr/` and `docs/fdr/` inherits the reasoning behind the codebase — how and why it is the way it is — and builds on it instead of re-deciding. Process skills run the work; this pack keeps its reasoning for the next agent.

## Route every decision to its record

| What was decided | Skill |
|---|---|
| Architecture or tech: stack, storage, data models, protocols, APIs, security, deployment | `adr` |
| A feature: what it does for users, its design choices, permissions, rationale | `fdr` |
| Audit all architecture records for drift | `adr-review` |
| Audit all feature records against the codebase | `fdr-review` |

Ambiguous call? The axis is reach: a choice that other features or services inherit is architecture (`adr`); a choice only this feature feels is a feature decision (`fdr`).

## Alongside superpowers

The superpowers process skills — `superpowers:brainstorming`, `superpowers:writing-plans`, `superpowers:executing-plans` / `superpowers:subagent-driven-development`, `superpowers:systematic-debugging`, `superpowers:finishing-a-development-branch` — are where decisions actually happen. Each phase both reads and writes the log:

| Phase | Read first | Then write |
|---|---|---|
| brainstorming | `docs/adr/INDEX.md` and `docs/fdr/INDEX.md` — an accepted record already settled its subject: present options inside it, or name the supersession you'd need | each choice the user accepts |
| writing-plans | the records each task touches | cite those records in the task text as constraints; log choices made while sequencing |
| executing / subagent dispatch | — | a subagent that departs from the plan just made a decision: record it before the next dispatch |
| briefing a subagent | the records the task touches | hand the subagent those records — the *why* rides with the brief, not just the *what* |
| debugging | the area's records, before anything: a deliberate design fails like a bug | fixes that harden into design choices |
| finishing a branch | — | the records join the change set: commit them with the work they describe |

## Discipline

- Record while the reasoning is in hand — mid-task, unprompted. Write the record, then continue the task.
- Read the log before designing in an area, even when you intend to write nothing.
- First record in a repo seeds the log: create `docs/adr/INDEX.md` or `docs/fdr/INDEX.md`; each record skill carries its TOC header.
- One decision touching both kinds: write each record and cite the counterpart (citations flow FDR → ADR).

## Red flags — these mean write the record now

- "This choice is too small to log" — small choices outlive their authors.
- "I'll write it up later" — later arrives without the reasoning.
- "The code and comments already say it" — they can't carry the why.
- "The plan or spec covers it" — they say what to build, not why it's shaped this way.
- "The human should ask if they want it" — the point is nobody has to ask.

## Completion criterion

A decision made here that will still matter in a month ends with: record file on disk, an `INDEX.md` row, the routed skill's template followed, and the record committed with the work it describes.
