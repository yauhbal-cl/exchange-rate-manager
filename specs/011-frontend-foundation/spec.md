# Feature Specification: Frontend Foundation

**Feature Branch**: `011-frontend-foundation`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "Frontend Foundation. Set up the Angular application with a scalable structure and navigation across the three core business views. Allow the application to connect to different backend environments during local development without requiring source-code changes. Automatically generate the frontend API layer from the agreed backend contract, improving consistency between frontend and backend, reducing integration errors, and lowering ongoing maintenance effort."

## Clarifications

### Session 2026-08-23

- Q: Which business view should load at the application's base address when none is selected yet? → A: Currency rate lookup
- Q: Does the persistent navigation element need explicit accessibility support (keyboard navigation, screen-reader active-view announcement)? → A: Yes — require keyboard operability + active-view indicated to assistive tech (e.g. `aria-current`)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Navigate between core business views (Priority: P1)

A user opens the application and moves between the three core business areas — currency rate
lookup, usage analytics, and AI trend insight — using a persistent navigation element, without
reloading the whole application or losing the overall app shell.

**Why this priority**: Navigation is the skeleton every other feature hangs on. Without it there
is no usable application to place future screens into — this is the minimum viable shell.

**Independent Test**: Can be fully tested by loading the application and clicking through each of
the three navigation entries, confirming the correct view loads and the navigation element remains
visible and shows which view is active.

**Acceptance Scenarios**:

1. **Given** the application is loaded, **When** the user selects a business view from the
   navigation, **Then** the corresponding view is displayed and the navigation indicates it as the
   active selection.
2. **Given** the user is on one business view, **When** they select a different view from the
   navigation, **Then** the application switches to the new view without a full browser page
   reload.
3. **Given** the user loads the application at its base address, **When** no specific view has
   been selected yet, **Then** the application displays the currency rate lookup view as the
   default rather than a blank screen.
4. **Given** the user navigates directly to a specific view's address (e.g., via bookmark or
   refresh), **When** the page loads, **Then** the correct view is displayed rather than always
   falling back to the default.

---

### User Story 2 - Point the application at different backend environments (Priority: P2)

A developer running the application locally needs to point it at different backend instances (for
example, a local backend, a shared development backend, or a staging backend) by changing
configuration, not application source code.

**Why this priority**: Unblocks efficient local development and testing against multiple backend
setups; without it, every environment switch requires a code change and rebuild, which slows down
day-to-day development.

**Independent Test**: Can be fully tested by changing only the environment configuration (no
source file edits) and confirming the running application issues its API calls against the newly
configured backend address.

**Acceptance Scenarios**:

1. **Given** the application is configured to point at a specific backend address, **When** the
   application makes any API call, **Then** the call is sent to that configured address.
2. **Given** a developer changes only the environment configuration value for the backend address,
   **When** the application is restarted (or rebuilt) with that new configuration, **Then** it
   connects to the newly specified backend without any source-code edits.
3. **Given** no environment-specific backend address has been provided, **When** the application
   starts, **Then** it falls back to a documented default suitable for local development.

---

### User Story 3 - Keep the frontend API layer consistent with the backend contract (Priority: P3)

A developer updates the agreed backend API contract and regenerates the frontend's API access
layer from it, so that the calls, request/response shapes, and types available to the frontend
always reflect the current contract without hand-writing or hand-maintaining that layer.

**Why this priority**: Reduces integration errors and maintenance effort over time, but the
application can be initially built and demoed with the first-generated layer in place; the ongoing
regeneration workflow is valuable but not blocking for an initial usable shell.

**Independent Test**: Can be fully tested by running the generation step against the current
backend contract and confirming a complete, working API access layer is produced that the rest of
the application can call, with no manually written request/response handling code.

**Acceptance Scenarios**:

1. **Given** the agreed backend contract, **When** the API layer generation step is run, **Then** a
   complete set of typed API access functions/methods matching the contract's operations is
   produced.
2. **Given** the backend contract changes (e.g., a field or endpoint is added), **When** the
   generation step is re-run, **Then** the frontend API layer reflects the updated contract without
   any manual edits to generated files.
3. **Given** the generated API layer, **When** any part of the application needs to call the
   backend, **Then** it does so through the generated layer rather than through hand-written HTTP
   call code.

---

### Edge Cases

- What happens when the configured backend address is unreachable? The application shell and
  navigation must still load; only the specific view's data-dependent content shows a clear
  error/unavailable state.
- What happens when a user navigates to an address that doesn't correspond to any of the three
  business views? The application shows a clear not-found state rather than a blank page or crash.
- What happens if generated API layer files are hand-edited and then regenerated? The manual edits
  are silently overwritten, since regeneration always replaces the generated output in full.
- What happens when the backend contract is temporarily invalid or unreachable during generation?
  Generation fails clearly and the previously generated layer is left untouched rather than
  partially overwritten.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The application MUST provide a persistent navigation element that lists the three
  core business views (currency rate lookup, usage analytics, AI trend insight) and always
  indicates which one is currently active. Every navigation entry MUST be operable via keyboard
  alone, and the active entry MUST be identifiable to assistive technology (e.g., via an
  `aria-current` attribute or equivalent), not conveyed by visual styling alone.
- **FR-002**: The application MUST support switching between the three business views without a
  full browser page reload.
- **FR-003**: The application MUST support direct/bookmarkable addressing of each business view, so
  loading or refreshing a view's address displays that view directly.
- **FR-004**: The application MUST display the currency rate lookup view as the default when
  loaded at its base address with no specific view selected.
- **FR-005**: The application MUST display a clear not-found state for addresses that do not match
  any defined view.
- **FR-006**: The application's structure MUST separate each business view into its own
  independently maintainable unit, so new business views or features can be added without
  restructuring existing ones.
- **FR-007**: The backend base address the application connects to MUST be controlled through
  environment configuration, not hard-coded in application source code.
- **FR-008**: The application MUST allow the backend base address to be changed for local
  development without requiring a source-code change.
- **FR-009**: The application MUST use a documented default backend address for local development
  when no environment-specific override is supplied.
- **FR-010**: The frontend's API access layer (request/response handling and data types for every
  backend operation) MUST be produced by an automated generation step driven by the agreed backend
  contract, not hand-written.
- **FR-011**: The generation step MUST be re-runnable on demand so the frontend API layer can be
  refreshed whenever the backend contract changes.
- **FR-012**: Generated API layer output MUST be treated as replaceable build output — the
  application's hand-written code MUST NOT require edits to the generated files themselves to
  function.
- **FR-013**: All application code that calls the backend MUST go through the generated API access
  layer rather than ad hoc, hand-written HTTP request code.
- **FR-014**: If the configured backend is unreachable, the application shell and navigation MUST
  remain usable; only the affected view's data-dependent content MUST show a clear
  error/unavailable indication.

### Key Entities

- **Business View**: One of the three top-level application sections (currency rate lookup, usage
  analytics, AI trend insight) a user navigates to; has a name, an address, and its own displayed
  content.
- **Environment Configuration**: The set of deployment-time values (chiefly the backend base
  address) that determine how a given running instance of the application behaves, distinct from
  its source code.
- **Backend Contract**: The agreed, versioned definition of the backend's available operations and
  data shapes that the frontend API layer generation step consumes as its input.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can reach any of the three core business views from the application's initial
  load in two actions or fewer (e.g., load the app, then one navigation click).
- **SC-002**: A developer can retarget the running local application to a different backend address
  using only configuration changes, with zero source-code edits, verified by inspecting the code
  diff after the switch.
- **SC-003**: Regenerating the frontend API layer after a backend contract change requires a single
  command/step and produces a fully usable API layer with zero manual follow-up edits.
- **SC-004**: 100% of the application's backend calls originate from the generated API layer, with
  zero hand-written HTTP request code paths, verified by code review.
- **SC-005**: When the configured backend is unreachable, the application shell and navigation
  remain interactive (not blank or crashed) in 100% of manual verification attempts.
- **SC-006**: Every navigation entry can be reached and activated using only the keyboard, and the
  currently active entry is exposed to assistive technology (e.g., `aria-current`), verified by
  keyboard-only and screen-reader manual walkthroughs.

## Assumptions

- The three core business views correspond to the backend's existing rate lookup, usage analytics,
  and AI trend insight capabilities; no additional business views are in scope for this feature.
- "Different backend environments during local development" refers to switching the backend base
  address via configuration (e.g., local backend vs. a shared/staging backend); it does not require
  building a full multi-environment deployment/release pipeline as part of this feature.
- The agreed backend contract is the existing shared API contract document already used to define
  backend behavior; this feature consumes it as-is rather than authoring or altering it.
- Visual design, branding, and detailed styling of each business view's content are out of scope
  for this feature — this feature covers the application shell, navigation, environment
  configuration, and the generated API layer only.
- Authentication/authorization for accessing the application or its views is out of scope; the
  three views are assumed to be accessible to any user of the running application.
