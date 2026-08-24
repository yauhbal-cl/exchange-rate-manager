# Feature Specification: Query Timestamp History (formerly referred to as "Query Date History")

**Feature Branch**: `016-query-date-history`

**Created**: 2026-08-24

**Status**: Draft

**Input**: User description: "changes in Exchange Rate API. We should record dates on which queries were made. We should record date for each currency participated in query. And serve it in Analytics Endpoint for as part of the response."

## Clarifications

### Session 2026-08-24

- Q: How should the per-currency query history be bounded in the analytics response? → A:
  Reuse the existing recency filter (`recentDays`) as the date window — no new option. When it is
  supplied it both selects which currencies appear (existing behaviour, unchanged) and trims each
  currency's returned history to that same window. When it is omitted, a documented default window is
  applied to the returned history while currency selection stays unfiltered as it is today.
- Q: Does "dates on which queries were made" mean one de-duplicated calendar date per day, or the
  full moment of each individual query? → A: Full timestamps. Every successful query records its own
  timestamp for each participating currency — no per-day de-duplication, so a currency queried five
  times in one day has five recorded query timestamps.
- Q: Should the response also cap how many timestamps each currency can return, or is the time window
  the only limit? → A: Window only, no count cap. Every query timestamp inside the applied window is
  returned in full, with no truncation and no per-currency maximum; response size is expected to scale
  with query traffic, and clients that want less data narrow the window.
- Q: How long should recorded query timestamps be kept in storage before old ones are deleted? → A:
  Retain 365 days, then delete older query timestamps on a scheduled purge. Purging never reduces a
  currency's lifetime query count or its last-queried value, so history can be shorter than the count
  implies for activity older than a year.
- Q: For currencies that already have usage recorded today, should the change create one starting
  history entry from their existing last-queried value, or should their history begin empty? → A:
  Seed exactly one history entry per existing usage record, taken from its current last-queried
  value. Counts are not altered and no additional entries are invented.
- Q: What concrete response-time target should the analytics endpoint meet once it returns full query
  history? → A: 95th-percentile response under 1 second for the default 90-day window, measured
  against a reference dataset of roughly 100,000 retained query timestamps spread across the full
  currency set.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See every moment a currency was queried (Priority: P1)

As an operator monitoring the platform, I want the usage analytics response to tell me not just
*how many times* a currency was queried and *when it was last* queried, but *the moment of every
query it took part in*, so that I can see activity patterns over time — bursts, quiet periods, gaps,
and time-of-day concentration — instead of a single total and a single most-recent timestamp.

**Why this priority**: This is the whole point of the feature. Today's analytics collapse all
history into a count plus one timestamp, which cannot answer "was this currency used steadily or
all in one burst?". Delivering only this story already gives operators the new insight.

**Independent Test**: Perform several rate queries involving a currency at distinct moments, then
request the usage analytics; verify the response lists exactly those query timestamps for that
currency, in addition to the existing count and last-queried values.

**Acceptance Scenarios**:

1. **Given** a currency pair has been queried at three distinct moments, **When** a client requests
   usage analytics, **Then** the entry for each of the two currencies lists exactly those three
   query timestamps.
2. **Given** a rate query names a source and a target currency, **When** the query succeeds,
   **Then** the query timestamp is recorded for **both** currencies, not only one of them.
3. **Given** the same currency is queried five times within one day, **When** analytics are
   requested, **Then** all five query timestamps are listed individually — they are not collapsed
   into one entry per day — and the existing query count also reads five.
4. **Given** a currency the platform holds rate data for has never been queried, **When**
   analytics are requested, **Then** its entry is still returned, with an empty timestamp list (and
   its existing count of zero and absent last-queried value unchanged).
5. **Given** a currency was queried at several moments, **When** analytics are requested, **Then**
   the timestamps come back in a stable, chronological order on every call, never in arbitrary
   order.
6. **Given** a currency has recorded query timestamps, **When** analytics are requested, **Then**
   the newest listed timestamp agrees with the existing last-queried value for that currency — the
   two never contradict each other.

---

### User Story 2 - Keep the query history from bloating the response (Priority: P2)

As a client of the analytics endpoint (operator dashboard or script), I want the recency window I
already pass to also decide how much query history I receive, so that a heavily used currency does
not force me to download and parse an enormous list of timestamps when I only care about recent
activity — and so I do not have to learn a second option to say the same thing.

**Why this priority**: Correctness of the data (Story 1) matters first; the response stays perfectly
usable at low query volumes. But because every single query now adds a timestamp for two currencies,
the response grows with traffic as well as with time, so bounding is needed before production use.

**Independent Test**: Record query timestamps spanning a long period for one currency, request
analytics with a recency window covering only part of that period, and verify only timestamps inside
the window are returned while the currency's lifetime query count remains the full total.

**Acceptance Scenarios**:

1. **Given** a currency has recorded query timestamps spanning a long period, **When** a client
   requests analytics with a recency window, **Then** only that currency's query timestamps inside
   the window are listed, and the unbounded lifetime query count is still reported in full.
2. **Given** a client requests analytics with no recency window supplied, **When** the response is
   built, **Then** each currency's timestamps are trimmed to the documented default window rather
   than returned unbounded, while currency selection stays unfiltered exactly as it is today.
3. **Given** a client supplies a recency window wider than the default, **When** the response is
   built, **Then** the supplied window is honoured in full — the default never silently narrows an
   explicit request.
4. **Given** a recency window is supplied, **When** a currency passes that window's filter, **Then**
   its timestamp list is never empty — the window that admitted the currency necessarily contains at
   least the moment it was last queried.
5. **Given** no recency window is supplied and a currency has rate data but was never queried,
   **When** analytics are requested, **Then** it is still listed with an empty timestamp list.
6. **Given** a currency accumulated a very large number of query timestamps inside the applied
   window, **When** analytics are requested, **Then** every one of them is returned — the response is
   never silently truncated to a maximum count, so a client can trust that what it received is the
   complete history for that window.

---

### User Story 3 - Existing analytics consumers keep working (Priority: P3)

As a maintainer of an already-shipped client of the usage analytics endpoint (including the existing
usage analytics dashboard), I want this change to be purely additive, so that nothing I already rely
on changes shape or meaning when the query history ships.

**Why this priority**: Protects work already in production rather than adding new capability, so it
ranks below the new value — but it must hold before release.

**Independent Test**: Exercise the analytics endpoint the way an existing client does — with and
without the current ranking and recency options — and verify every previously returned field is
present with unchanged meaning and ordering.

**Acceptance Scenarios**:

1. **Given** an existing client that reads only the per-currency code, query count, and last-queried
   value, **When** it calls the analytics endpoint after this change, **Then** all three are present
   and unchanged in meaning.
2. **Given** an existing client using the ranking limit and/or the recency filter, **When** it calls
   the endpoint, **Then** currency selection, ranking, and tie-break ordering behave exactly as they
   did before this change.
3. **Given** currencies that already had usage recorded before this feature shipped, **When**
   analytics are requested afterwards, **Then** their historical counts and last-queried values are
   preserved (see Assumptions for what query history exists for pre-existing usage).

---

### Edge Cases

- A rate query is rejected as invalid (unknown currency code, identical source and target, or
  malformed input): no query timestamp is recorded for any currency involved.
- A rate query is accepted but no stored rate exists for the requested date: the query fails, so no
  query timestamp is recorded — consistent with the existing query counters.
- Two queries involving the same currency arrive concurrently: both are recorded, as two separate
  timestamps, with neither lost.
- Two queries involving the same currency land on the same moment as far as the clock can
  distinguish: both are still recorded as separate entries — identical timestamps are permitted and
  must not collapse or be rejected.
- A query lands either side of midnight: nothing special happens, because the recorded value is an
  absolute moment rather than a calendar date; timestamps are recorded and served in a single fixed
  reference time zone so no client has to guess the offset.
- An administrative or scheduled rate-collection run (manual refresh or the daily ingestion job):
  records no query timestamps and touches no counters.
- A currency's rate data is present but it has never been queried: empty timestamp list, not a
  missing field or a null placeholder.
- Query history grows large for a heavily used currency — every query adds a timestamp for two
  currencies, so volume grows with traffic, not just with elapsed time. The response is bounded only
  by the applied window (Story 2), so a busy currency legitimately returns a large list; it is never
  clipped to a maximum count. Clients that want a smaller response narrow the window.
- A recency window is supplied that is wider than the default window: the supplied value wins
  outright; the default applies only when no window is supplied at all.
- A recency window wider than the 365-day retention period is supplied: the request succeeds and
  returns the retained history, which simply starts at the retention boundary.
- A currency was last queried more than 365 days ago and not since: its history has been purged, so
  it reports a positive lifetime query count and a last-queried value with an empty timestamp list —
  the count is not rewritten to match the shortened history.
- A pre-existing usage record's last-queried value is already older than the retention period: it is
  still seeded as one entry, and the next purge removes it — the same rule applies to seeded and
  live-recorded history, with no special case.
- The rollout seeding step is applied more than once (a re-run or a replayed deployment): each
  pre-existing record still ends up with exactly one seeded entry, not several.
- A purge runs while queries are being recorded: newly recorded timestamps are unaffected, queries
  keep succeeding, and only history past the retention boundary is removed.
- A currency's only recorded query timestamps are older than the applied window: with a window
  supplied the currency is excluded by the existing recency filter; with no window supplied the
  currency is listed with an empty timestamp list while its lifetime count still shows the older
  activity.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST record the moment (timestamp) of every successful exchange-rate query for
  **each** currency participating in that query — both the source and the target currency.
- **FR-002**: System MUST record one entry per participating currency per query, with no per-day or
  per-period de-duplication — a currency involved in five successful queries has five recorded query
  timestamps, however close together those queries fell.
- **FR-003**: Recording query timestamps MUST be safe under concurrent load — simultaneous queries
  involving the same currency MUST NOT lose a record, drop one as a perceived duplicate, or corrupt
  the existing query counter.
- **FR-004**: A currency's lifetime query count MUST account for every successful query it took part
  in, and MUST equal the number of query timestamps recorded for it while those timestamps are still
  retained. Purging expired history (FR-022) MUST NOT reduce the count, so for activity older than
  the retention period the count may legitimately exceed the retained history.
- **FR-005**: Recorded timestamps MUST be captured and served in a single fixed reference time zone
  with an explicit offset, so the moment a client reads does not depend on the host's local time zone
  configuration.
- **FR-006**: Query timestamps MUST be recorded only for query activity. Administrative or scheduled
  rate collection (manual refresh, daily ingestion) MUST NOT record query timestamps, consistent with
  the existing rule that such operations do not alter usage counters.
- **FR-007**: A query that fails validation, or for which no rate can be resolved, MUST NOT record a
  query timestamp for any currency — recording happens only for queries that successfully return a
  rate.
- **FR-008**: The usage analytics response MUST include, for each returned currency, the collection
  of moments at which that currency was queried.
- **FR-009**: The returned timestamps for a currency MUST be in chronological order (oldest to
  newest) and MUST be identical across repeated identical requests.
- **FR-010**: A currency with no recorded query timestamps MUST be returned with an empty collection
  rather than an omitted field or a null value.
- **FR-011**: The span of query history returned per currency MUST be bounded by the existing recency
  window option; no new request option is introduced for this purpose. When a recency window is
  supplied, each returned currency's timestamps MUST be trimmed to that same window.
- **FR-012**: When no recency window is supplied, each currency's returned timestamps MUST be trimmed
  to a documented default window (see Assumptions); the response MUST NOT return history older than
  the applied window.
- **FR-013**: The applied window MUST be the only limit on returned history. The response MUST NOT
  impose a maximum number of timestamps per currency, MUST NOT truncate, and MUST NOT sample — every
  recorded timestamp inside the window is returned, however many there are.
- **FR-014**: An explicitly supplied recency window MUST be honoured in full even when it is wider
  than the default window — the default MUST NOT narrow an explicit request.
- **FR-015**: Trimming the returned timestamps MUST NOT alter which currencies appear in the
  response beyond the recency filter's existing currency-selection behaviour, and MUST NOT alter the
  reported lifetime query count for any currency.
- **FR-016**: All fields the analytics response returns today (per-currency code, query count, and
  last-queried value) MUST remain present with unchanged meaning; the timestamp collection is an
  addition, not a replacement.
- **FR-017**: The existing ranking limit and recency-filter options MUST retain their current
  behaviour for currency selection, ranking, and tie-breaking.
- **FR-018**: The API contract MUST be updated to describe the new timestamp collection, so that both
  the documented contract and every generated client reflect the added field.
- **FR-019**: Usage records that existed before this feature shipped MUST retain their query counts
  and last-queried values unchanged.
- **FR-020**: Rollout MUST seed exactly one query timestamp per pre-existing usage record, equal to
  that record's current last-queried value. No further entries may be invented to make the history
  match the existing count, and seeding MUST NOT change any count.
- **FR-021**: Seeding MUST be a one-time rollout step that produces the same result if it is applied
  more than once — it MUST NOT accumulate duplicate seeded entries on repeat application.
- **FR-022**: Recorded query timestamps MUST be retained for 365 days and MUST be deleted once older
  than that, by a recurring automated purge rather than by manual intervention.
- **FR-023**: Purging expired query timestamps MUST NOT alter any currency's lifetime query count,
  its last-queried value, or which currencies the analytics endpoint reports on.
- **FR-024**: The purge MUST behave correctly when the service runs as multiple instances — expired
  history is removed once per scheduled run, and a purge running concurrently with live query
  recording MUST NOT lose newly recorded timestamps or block queries from succeeding.
- **FR-025**: A requested window wider than the retention period MUST succeed and return whatever
  history is still retained, rather than failing or implying that missing older activity never
  happened.

### Key Entities *(include if data involved)*

- **Currency Usage**: The existing per-currency usage record — the currency code, its lifetime query
  count, and the moment it was last queried. Unchanged by this feature except that it now owns a
  history of query events.
- **Currency Query Event**: A new record stating "this currency was queried at this moment". One is
  created for each participating currency each time a query succeeds, so the same currency can hold
  many events for the same day, and two events may even share an identical timestamp. Retained for
  365 days from the moment it records, then purged. Carries no requester identity.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a currency involved in N successful queries, the analytics response lists exactly
  those N query moments — none missing, none extra, none collapsed together — in 100% of verification
  runs (within the applied window).
- **SC-002**: Every successful rate query results in a recorded timestamp for both of its currencies:
  across a run of mixed queries, 100% of participating currencies have the query's moment recorded.
- **SC-003**: Under 1,000 concurrent queries involving one currency, that currency ends with exactly
  1,000 recorded query timestamps and a query count of exactly 1,000 — no losses, no de-duplication.
- **SC-004**: Operators can determine when a currency was queried across the returned window from a
  single analytics request, with no follow-up requests needed.
- **SC-005**: Analytics responses for the default 90-day window complete in under 1 second at the
  95th percentile, measured against a reference dataset of roughly 100,000 retained query timestamps
  spread across the full set of tracked currencies.
- **SC-006**: Existing analytics clients require zero changes to keep working: every field and option
  they use behaves identically before and after the change.
- **SC-007**: No administrative refresh or scheduled collection run, however many times it executes,
  adds a single query timestamp or increments a single counter.
- **SC-008**: No analytics response ever returns timestamps older than the applied window — verified
  both with an explicit window and with none supplied (default window applied).
- **SC-009**: For every currency in every response, the lifetime query count and the retained history
  agree for activity inside the retention period: within that period the count of returned timestamps
  never disagrees with the recorded activity, and any shortfall is attributable only to purged
  history or the applied window.
- **SC-010**: For a currency with a large number of timestamps inside the applied window, the
  response contains 100% of them — zero truncation, zero sampling — so a client can treat the
  returned history as complete for that window.
- **SC-011**: After the automated purge has run, zero query timestamps older than 365 days remain in
  storage, and every currency's lifetime query count and last-queried value are exactly what they
  were before the purge.
- **SC-012**: Stored query history stays bounded over time: with steady traffic the retained volume
  stops growing once the platform has been running longer than the retention period, rather than
  increasing without limit.
- **SC-013**: Immediately after rollout, every currency that already had usage recorded has exactly
  one history entry, equal to its last-queried value, and its query count is identical to what it was
  before rollout.

## Assumptions

- "Dates on which queries were made" means the full moment of each query, not a de-duplicated
  calendar date: every successful query contributes its own timestamp for each participating
  currency. The history is therefore an append-only log of query events, and its size grows with
  query traffic as well as with elapsed time.
- The existing last-queried value stays in the response for backward compatibility even though it is
  now derivable from the newest recorded query event.
- "Queries" means the same activity that increments the existing usage counters today — successful
  spread-adjusted rate lookups. Historical trend retrieval and AI insight requests do not increment
  counters today and are therefore also out of scope for date recording; bringing them in would be a
  separate, deliberate change to what "usage" means.
- Both currencies in a pair are treated as participants, matching the existing counter behaviour of
  incrementing for the source and the target.
- The fixed reference time zone for recording and serving query moments is UTC, matching how existing
  timestamps are stored.
- Recording a query event happens as part of the same unit of work as the existing counter update, so
  counts and history cannot drift apart.
- Pre-existing usage records get exactly one seeded history entry, from their existing last-queried
  value — the only query moment the platform ever actually stored for them. The rest of their prior
  activity is unrecoverable and is deliberately not reconstructed, so an old count with a
  single-entry history is expected and correct rather than a defect.
- Scope is the backend API and its contract: recording the query moments and serving them in the
  analytics response. Changing the existing usage analytics dashboard to display them is a separate
  frontend change, not part of this feature.
- The default window, applied when no recency window is supplied, is the last 90 days. It bounds only
  the history listed per currency — it does not filter which currencies are returned, so the
  no-options response still lists every currency including never-queried ones, exactly as today.
- The purge runs on a recurring schedule (daily is sufficient — retention is a boundary measured in
  days, not an exact-to-the-second cutoff), reusing the platform's existing approach to safe
  scheduled work across multiple instances rather than introducing a new mechanism.
- The reference dataset behind the response-time target (roughly 100,000 retained query timestamps
  across the full currency set) stands in for expected production volume; the target is a per-request
  latency expectation, not a throughput or concurrency commitment.
- Retention is measured backwards from the current moment, so the retained history is a rolling
  365-day trailing window.
- Response size scaling with query traffic is an accepted tradeoff: completeness within the window is
  valued over a predictable response size, and the window is the client's lever for controlling
  volume. If traffic later makes this untenable, adding a cap or pagination would be a deliberate
  follow-up change, not something this feature quietly does.
- The recency window keeps its existing meaning for currency selection; this feature adds a second,
  consistent use of the same value for trimming the returned history, rather than redefining it.
- The set of currencies the analytics endpoint reports on is unchanged — currencies known from stored
  rate data, including never-queried ones.
- Query history is operational analytics data, not personal data: it records which currency was
  queried at which moment, with no requester identity attached.
