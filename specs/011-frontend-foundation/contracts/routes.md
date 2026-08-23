# Contract: Top-Level Route Table

This feature's externally-visible interface is the browser-addressable route table (not a network
API — the network contract, `contracts/openapi.yaml`, is upstream input, unchanged by this
feature; see [data-model.md](../data-model.md)).

| Path | Resolves to | Notes |
|---|---|---|
| `''` | redirect → `rate-lookup` | `pathMatch: 'full'`; satisfies FR-004 (default view) |
| `rate-lookup` | `features/rate-lookup` (lazy) | business view 1 |
| `usage-analytics` | `features/usage-analytics` (lazy) | business view 2 |
| `ai-insight` | `features/ai-insight` (lazy) | business view 3 |
| `**` | `not-found` | satisfies FR-005 (wildcard, catches unknown addresses) |

**Guarantees**:
- Direct navigation/refresh at `rate-lookup`, `usage-analytics`, or `ai-insight` renders that view
  directly (FR-003) — no client-side-only state required to reach any view.
- Switching between any two of the above paths never triggers a full browser reload (FR-002) —
  enforced by using Angular Router (`provideRouter`) exclusively, no `<a href>` full-page links
  between these paths.
- The shell (nav + `<router-outlet>`) renders for every path above, including `not-found` — nav
  never disappears (FR-001, FR-014).
- Backend reachability has no bearing on route resolution — only the affected view's inner content
  degrades to an error state when its own generated-service call fails (FR-014).
