# Contracts: Backend Spring AI Slice (Trend Insight Endpoint)

**Supersedes note**: This feature's contracts folder previously held only the infra-only
prerequisite pass's note (adding Ollama to `docker-compose.yml`, which exposes no HTTP contract of
this repository's own). This pass adds a real HTTP contract, documented in
[`trend-insight-endpoint.yaml`](./trend-insight-endpoint.yaml).

Per this repo's convention (see `005-analytics-endpoint`'s `contracts/analytics-endpoints.yaml`),
this file is a **design-phase reference** — the proposed additions to the root
`contracts/openapi.yaml` — not a second source of truth. The actual edit to
`contracts/openapi.yaml`, and the subsequent backend/frontend codegen regeneration, happens during
implementation (tracked as a task in `tasks.md`), per CLAUDE.md's "edit the contract first, then
regenerate both sides" workflow.
