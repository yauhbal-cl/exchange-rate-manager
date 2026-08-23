import { customRangeError, PERIOD_PRESETS, resolveRange, subtractMonths } from './period-presets';

const TODAY = '2026-08-23';

describe('resolveRange', () => {
  it('resolves the 7D preset to a 7-day trailing window ending today', () => {
    expect(resolveRange({ kind: 'preset', id: '7D' }, TODAY)).toEqual({
      startDate: '2026-08-16',
      endDate: '2026-08-23',
    });
  });

  it('resolves the 15D preset to a 15-day trailing window ending today', () => {
    expect(resolveRange({ kind: 'preset', id: '15D' }, TODAY)).toEqual({
      startDate: '2026-08-08',
      endDate: '2026-08-23',
    });
  });

  it('resolves the 1M preset to a 1-calendar-month trailing window ending today', () => {
    expect(resolveRange({ kind: 'preset', id: '1M' }, TODAY)).toEqual({
      startDate: '2026-07-23',
      endDate: '2026-08-23',
    });
  });

  it('resolves the 3M preset to a 3-calendar-month trailing window ending today', () => {
    expect(resolveRange({ kind: 'preset', id: '3M' }, TODAY)).toEqual({
      startDate: '2026-05-23',
      endDate: '2026-08-23',
    });
  });

  it('resolves the 6M preset to a 6-calendar-month trailing window ending today', () => {
    expect(resolveRange({ kind: 'preset', id: '6M' }, TODAY)).toEqual({
      startDate: '2026-02-23',
      endDate: '2026-08-23',
    });
  });

  it('passes a custom range through unchanged', () => {
    const selection = { kind: 'custom' as const, startDate: '2026-01-01', endDate: '2026-01-31' };
    expect(resolveRange(selection, TODAY)).toEqual({
      startDate: '2026-01-01',
      endDate: '2026-01-31',
    });
  });

  it('defines exactly the five named presets', () => {
    expect(PERIOD_PRESETS.map((preset) => preset.id)).toEqual(['7D', '15D', '1M', '3M', '6M']);
  });

  it("the 6M preset's startDate matches the FR-006 cap boundary for the same today", () => {
    const sixMonthPreset = resolveRange({ kind: 'preset', id: '6M' }, TODAY);
    expect(sixMonthPreset.startDate).toBe(subtractMonths(TODAY, 6));
  });
});

describe('subtractMonths', () => {
  it('clamps to the shorter month (Aug 31 - 1 month = Jul 31)', () => {
    expect(subtractMonths('2026-08-31', 1)).toBe('2026-07-31');
  });

  it('clamps to Feb 28 in a non-leap year (Mar 31 - 1 month)', () => {
    expect(subtractMonths('2026-03-31', 1)).toBe('2026-02-28');
  });

  it('clamps to Feb 29 in a leap year (Mar 31 - 1 month, 2028)', () => {
    expect(subtractMonths('2028-03-31', 1)).toBe('2028-02-29');
  });

  it('rolls back across a year boundary', () => {
    expect(subtractMonths('2026-01-15', 2)).toBe('2025-11-15');
  });
});

describe('customRangeError', () => {
  it('returns null for a valid range within 6 months', () => {
    expect(customRangeError('2026-06-01', '2026-08-01')).toBeNull();
  });

  it('returns an error when the start date is after the end date', () => {
    expect(customRangeError('2026-08-10', '2026-08-01')).not.toBeNull();
  });

  it('returns an error when the span exceeds 6 months', () => {
    expect(customRangeError('2026-01-01', '2026-08-01')).not.toBeNull();
  });

  it('accepts a span of exactly 6 months (the same boundary as the 6M preset)', () => {
    const endDate = '2026-08-23';
    const startDate = subtractMonths(endDate, 6);
    expect(customRangeError(startDate, endDate)).toBeNull();
  });
});
