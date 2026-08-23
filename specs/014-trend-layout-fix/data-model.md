# Phase 1 Data Model: Historical Trends Full-Width Chart & AI Insights Layout

No data model changes. This fix is presentation-only (template/layout classes in
`historical-rates.ts`); it introduces no new entity, field, relationship, or state transition, and
touches no persisted or transmitted data shape.

The three UI elements named in the spec's Key Entities section (Trend Chart, AI Insights Section,
Historical Rates Container) are existing rendered UI regions, not data entities — their inputs
(`points`, `dailyChanges`, `periodHigh`, `periodLow`, `value`, `isLoading`, `error`,
`canGenerate`) are unchanged by this fix; only where and how wide each region renders changes.
