# Evidence Sources for Decision-Record Audits

Shared by `adr-review` and `fdr-review` — keep those skills installed together. Adapt paths to the project; consult root and path-specific `AGENTS.md` files first for its documentation layout.

| Category | Typical locations | What it proves |
|---|---|---|
| Decision-record inventory | `docs/adr/INDEX.md`, `docs/fdr/INDEX.md`, plus a directory listing of each folder | which records exist; titles, status, dates |
| Related records | `docs/fdr/`, `docs/rfc/`, `docs/design/`, `docs/features/`, or project equivalents | cross-citations between records |
| Architecture docs | `docs/architecture/`, `docs/ARCHITECTURE.md`, or the repository's equivalent | the architecture the docs currently claim |
| Canonical vocabulary | `docs/GLOSSARY.md`, terminology or domain-model docs | the terms a record should match |
| Public surface | API schemas, OpenAPI, protobuf/Thrift/GraphQL IDLs, REST/RPC route definitions, WebSocket protocols, generated clients | protocol and compatibility claims |
| Core implementation | service wiring, domain models, persistence code, migrations, background jobs, event handlers, integration boundaries | whether a decision is actually implemented |
| Client implementation | frontend, mobile, SDK, CLI, or other consumer code | client-side claims |
| Deployment and operations | infrastructure config, runtime configuration, observability, backup/restore, rollout docs | operational claims |
