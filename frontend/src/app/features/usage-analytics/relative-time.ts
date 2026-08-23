/**
 * Elapsed-time phrasing for the recent-activity panel (data-model.md §3, research.md §4).
 *
 * Pure functions of `(instant, now)` with no dependency: `now` is captured once when the page
 * component is created and passed in, so phrases are fixed at load time and never tick forward
 * (SC-006). The unit ladder is a declared, ordered lookup table rather than a conditional chain
 * (Constitution VII) — coarsening a rung or inserting one is a data change, not a code change.
 */

const SECOND_MS = 1_000;
const MINUTE_MS = 60 * SECOND_MS;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;
const MONTH_MS = 30 * DAY_MS;
const YEAR_MS = 12 * MONTH_MS;

/**
 * The distinct under-a-minute literal FR-012 requires — deliberately not
 * `Intl.RelativeTimeFormat.format(0, 'second')`, which reads as a plain "now".
 */
const JUST_NOW = 'just now';

/**
 * One rung of the elapsed-time ladder: ages below `ceilingMs` are phrased as whole `unit`s of
 * `unitMs` each (floored). Rungs are ordered finest-first and scanned in order.
 */
interface ElapsedRung {
  readonly unit: Intl.RelativeTimeFormatUnit;
  readonly unitMs: number;
  readonly ceilingMs: number;
}

/** Coarsest rung: unbounded, so the ladder is total for any finite age (data-model.md §3). */
const COARSEST_RUNG: ElapsedRung = {
  unit: 'year',
  unitMs: YEAR_MS,
  ceilingMs: Number.POSITIVE_INFINITY,
};

/**
 * The FR-012 ladder verbatim: `< 60 s` → the just-now literal (ages under the finest rung's
 * `unitMs`), `< 60 min` → minutes, `< 24 h` → hours, `< 30 d` → days, `< 12 mo` → months,
 * otherwise years.
 */
const ELAPSED_LADDER: readonly ElapsedRung[] = [
  { unit: 'minute', unitMs: MINUTE_MS, ceilingMs: HOUR_MS },
  { unit: 'hour', unitMs: HOUR_MS, ceilingMs: DAY_MS },
  { unit: 'day', unitMs: DAY_MS, ceilingMs: MONTH_MS },
  { unit: 'month', unitMs: MONTH_MS, ceilingMs: YEAR_MS },
  COARSEST_RUNG,
];

/** Age below which the just-now literal applies: the finest rung's own unit length (60 s). */
const JUST_NOW_CEILING_MS = MINUTE_MS;

/** Module-level singletons — one construction per load, not one per rendered entry. */
const RELATIVE_FORMAT = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
const ABSOLUTE_FORMAT = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

/**
 * Whether an ISO-8601 instant from the API can be phrased at all. An unparseable instant is
 * treated as absent: the caller drops the entry rather than rendering a broken phrase
 * (data-model.md §3).
 */
export function isParsableInstant(instant: string): boolean {
  return Number.isFinite(Date.parse(instant));
}

/**
 * Elapsed-time phrase for `instant` as of `now` (FR-012). Counts are floored onto the first
 * ladder rung whose ceiling the age clears; phrasing comes from
 * `Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })` in the viewer's own locale.
 *
 * Future instants (clock skew) clamp to the just-now literal — never negative, never
 * future-tense. An unparseable instant clamps the same way; callers filter with
 * {@link isParsableInstant} first.
 */
export function relativePhrase(instant: string, now: Date): string {
  const ageMs = now.getTime() - Date.parse(instant);
  if (!Number.isFinite(ageMs) || ageMs < JUST_NOW_CEILING_MS) {
    return JUST_NOW;
  }

  const rung = ELAPSED_LADDER.find((candidate) => ageMs < candidate.ceilingMs) ?? COARSEST_RUNG;
  return RELATIVE_FORMAT.format(-Math.floor(ageMs / rung.unitMs), rung.unit);
}

/**
 * The same instant as a local-timezone date and time-of-day, for the inspect/hover path
 * (FR-012a) — the viewer's own locale and zone, both date and time-of-day present.
 */
export function absoluteLocal(instant: string): string {
  return ABSOLUTE_FORMAT.format(new Date(instant));
}
