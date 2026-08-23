# Data Model: Consistent Error Response for Missing Required Query Parameters

Not applicable. This feature introduces no new persisted entity, no schema change, and no new DTO
type — it reuses the existing `ProblemDetail` response shape already defined by
`contracts/openapi.yaml` and already returned by every other handled exception in
`GlobalExceptionHandler`. See [research.md](./research.md) for confirmation that the contract
needs no change.
