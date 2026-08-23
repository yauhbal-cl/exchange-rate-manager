import { absoluteLocal, isParsableInstant, relativePhrase } from './relative-time';

/**
 * Every case below is a pure `(instant, now)` call — `now` is a fixed value passed in, never the
 * real clock and never a fake timer (data-model.md §3, research.md §4).
 */
const NOW = new Date('2026-08-23T12:00:00.000Z');

const SECOND = 1000;
const MINUTE = 60 * SECOND;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const MONTH = 30 * DAY;
const YEAR = 12 * MONTH;

/** ISO-8601 instant that is exactly `ageMs` old relative to `NOW`. */
const agedBy = (ageMs: number): string => new Date(NOW.getTime() - ageMs).toISOString();

/**
 * Locale-robust expectation: the ladder phrases are whatever the runtime's own
 * `Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })` produces, so these specs assert the
 * chosen unit and magnitude rather than English wording.
 */
const relative = (value: number, unit: Intl.RelativeTimeFormatUnit): string =>
  new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' }).format(-value, unit);

const JUST_NOW = 'just now';

describe('relativePhrase — under-a-minute literal (FR-012)', () => {
  it('returns the distinct just-now literal for an instant equal to now', () => {
    expect(relativePhrase(agedBy(0), NOW)).toBe(JUST_NOW);
  });

  it('returns the distinct just-now literal one second under the 60 s threshold', () => {
    expect(relativePhrase(agedBy(59 * SECOND), NOW)).toBe(JUST_NOW);
  });

  it('returns the distinct just-now literal one millisecond under the 60 s threshold', () => {
    expect(relativePhrase(agedBy(MINUTE - 1), NOW)).toBe(JUST_NOW);
  });

  it('does not use the Intl "now" phrase for the under-a-minute case', () => {
    expect(relativePhrase(agedBy(10 * SECOND), NOW)).not.toBe(relative(0, 'second'));
  });
});

describe('relativePhrase — minutes rung (60 s … 60 min)', () => {
  it('switches to minutes at exactly 60 s', () => {
    expect(relativePhrase(agedBy(MINUTE), NOW)).toBe(relative(1, 'minute'));
  });

  it('floors a partial minute to the whole minute count', () => {
    expect(relativePhrase(agedBy(3 * MINUTE + 45 * SECOND), NOW)).toBe(relative(3, 'minute'));
  });

  it('stays on minutes one second under the 60 min threshold', () => {
    expect(relativePhrase(agedBy(59 * MINUTE + 59 * SECOND), NOW)).toBe(relative(59, 'minute'));
  });
});

describe('relativePhrase — hours rung (60 min … 24 h)', () => {
  it('switches to hours at exactly 60 min', () => {
    expect(relativePhrase(agedBy(HOUR), NOW)).toBe(relative(1, 'hour'));
  });

  it('floors a partial hour to the whole hour count', () => {
    expect(relativePhrase(agedBy(5 * HOUR + 30 * MINUTE), NOW)).toBe(relative(5, 'hour'));
  });

  it('stays on hours one minute under the 24 h threshold', () => {
    expect(relativePhrase(agedBy(23 * HOUR + 59 * MINUTE), NOW)).toBe(relative(23, 'hour'));
  });
});

describe('relativePhrase — days rung (24 h … 30 d)', () => {
  it('switches to days at exactly 24 h', () => {
    expect(relativePhrase(agedBy(DAY), NOW)).toBe(relative(1, 'day'));
  });

  it('floors a partial day to the whole day count', () => {
    expect(relativePhrase(agedBy(2 * DAY + 6 * HOUR), NOW)).toBe(relative(2, 'day'));
  });

  it('stays on days one hour under the 30 d threshold', () => {
    expect(relativePhrase(agedBy(29 * DAY + 23 * HOUR), NOW)).toBe(relative(29, 'day'));
  });
});

describe('relativePhrase — months rung (30 d … 12 mo)', () => {
  it('switches to months at exactly 30 d', () => {
    expect(relativePhrase(agedBy(MONTH), NOW)).toBe(relative(1, 'month'));
  });

  it('floors a partial month to the whole month count', () => {
    expect(relativePhrase(agedBy(4 * MONTH + 10 * DAY), NOW)).toBe(relative(4, 'month'));
  });

  it('stays on months one day under the 12 mo threshold', () => {
    expect(relativePhrase(agedBy(YEAR - DAY), NOW)).toBe(relative(11, 'month'));
  });
});

describe('relativePhrase — years rung (12 mo and beyond)', () => {
  it('switches to years at exactly 12 mo', () => {
    expect(relativePhrase(agedBy(YEAR), NOW)).toBe(relative(1, 'year'));
  });

  it('floors a partial year to the whole year count', () => {
    expect(relativePhrase(agedBy(3 * YEAR + 5 * MONTH), NOW)).toBe(relative(3, 'year'));
  });

  it('keeps using years for a very old instant', () => {
    expect(relativePhrase(agedBy(25 * YEAR), NOW)).toBe(relative(25, 'year'));
  });
});

describe('relativePhrase — clock-skew clamp (spec edge case)', () => {
  it('clamps an instant one second in the future to the just-now literal', () => {
    expect(relativePhrase(agedBy(-1 * SECOND), NOW)).toBe(JUST_NOW);
  });

  it('clamps an instant hours in the future to the just-now literal', () => {
    expect(relativePhrase(agedBy(-6 * HOUR), NOW)).toBe(JUST_NOW);
  });

  it('clamps an instant years in the future to the just-now literal', () => {
    expect(relativePhrase(agedBy(-3 * YEAR), NOW)).toBe(JUST_NOW);
  });

  it('never produces a future-tense phrase for a future instant', () => {
    const futureTense = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
    expect(relativePhrase(agedBy(-5 * MINUTE), NOW)).not.toBe(futureTense.format(5, 'minute'));
  });
});

describe('relativePhrase — purity (SC-006, phrases fixed at load time)', () => {
  it('returns the same phrase for repeated calls with the same (instant, now)', () => {
    const instant = agedBy(3 * HOUR);
    expect(relativePhrase(instant, NOW)).toBe(relativePhrase(instant, NOW));
  });

  it('derives the phrase from the supplied now, not the real clock', () => {
    const instant = '2026-08-23T11:00:00.000Z';
    expect(relativePhrase(instant, new Date('2026-08-23T12:00:00.000Z'))).toBe(relative(1, 'hour'));
    expect(relativePhrase(instant, new Date('2026-08-25T11:00:00.000Z'))).toBe(relative(2, 'day'));
  });
});

describe('isParsableInstant — the unparseable-instant contract (data-model.md §3)', () => {
  it('accepts a UTC ISO-8601 instant', () => {
    expect(isParsableInstant('2026-08-23T10:15:00Z')).toBe(true);
  });

  it('accepts an ISO-8601 instant with a numeric UTC offset', () => {
    expect(isParsableInstant('2026-08-23T13:15:00+03:00')).toBe(true);
  });

  it('accepts an ISO-8601 instant with fractional seconds', () => {
    expect(isParsableInstant('2026-08-23T10:15:00.123Z')).toBe(true);
  });

  it('rejects a non-date string', () => {
    expect(isParsableInstant('not-an-instant')).toBe(false);
  });

  it('rejects an empty string', () => {
    expect(isParsableInstant('')).toBe(false);
  });

  it('rejects an ISO-shaped string with out-of-range components', () => {
    expect(isParsableInstant('2026-13-45T99:99:99Z')).toBe(false);
  });

  it('is the predicate a caller uses to drop an entry before phrasing it', () => {
    const instants = ['2026-08-23T10:15:00Z', 'not-an-instant', '2026-08-23T11:15:00Z'];
    expect(instants.filter(isParsableInstant)).toEqual([
      '2026-08-23T10:15:00Z',
      '2026-08-23T11:15:00Z',
    ]);
  });
});

describe('absoluteLocal — local date and time-of-day (FR-012a)', () => {
  it("matches the viewer's medium-date / short-time Intl formatting", () => {
    const instant = '2026-08-23T10:15:00Z';
    expect(absoluteLocal(instant)).toBe(
      new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
        new Date(instant),
      ),
    );
  });

  it('matches the same Intl formatting for an instant with a numeric UTC offset', () => {
    const instant = '2026-01-05T23:45:00+03:00';
    expect(absoluteLocal(instant)).toBe(
      new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
        new Date(instant),
      ),
    );
  });

  it('includes the time-of-day: two instants one hour apart format differently', () => {
    expect(absoluteLocal('2026-08-23T10:15:00Z')).not.toBe(absoluteLocal('2026-08-23T11:15:00Z'));
  });

  it('includes the date: two instants one day apart format differently', () => {
    expect(absoluteLocal('2026-08-23T10:15:00Z')).not.toBe(absoluteLocal('2026-08-24T10:15:00Z'));
  });

  it('returns the same string for repeated calls with the same instant', () => {
    expect(absoluteLocal('2026-08-23T10:15:00Z')).toBe(absoluteLocal('2026-08-23T10:15:00Z'));
  });
});
