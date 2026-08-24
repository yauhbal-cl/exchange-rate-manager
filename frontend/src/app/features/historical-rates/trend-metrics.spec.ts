import {
  computeDailyChanges,
  computeLatest,
  computePeriodChange,
  computePeriodHigh,
  computePeriodLow,
} from './trend-metrics';
import type { RateTrendPoint } from '../../api-client';

function point(rateDate: string, rate: string): RateTrendPoint {
  return { rateDate, rate };
}

describe('computeLatest', () => {
  it('returns null for an empty points array', () => {
    expect(computeLatest([])).toBeNull();
  });

  it('formats the last point to six decimal places', () => {
    const points = [point('2026-08-01', '0.9000000000'), point('2026-08-02', '0.9100000000')];
    const latest = computeLatest(points);
    expect(latest?.display).toBe('0.910000');
    expect(latest?.value.toString()).toBe('0.91');
  });
});

describe('computePeriodChange', () => {
  it('returns null for an empty points array', () => {
    expect(computePeriodChange([])).toBeNull();
  });

  it('returns null for a single point', () => {
    expect(computePeriodChange([point('2026-08-01', '0.9000000000')])).toBeNull();
  });

  it('computes the absolute and percent change across multiple points', () => {
    const points = [
      point('2026-08-01', '0.9000000000'),
      point('2026-08-02', '0.9500000000'),
      point('2026-08-03', '0.9900000000'),
    ];
    const change = computePeriodChange(points);
    expect(change?.absolute).toBe('0.09');
    expect(change?.percent).toBe('+10.00%');
  });

  it('formats a negative change with a leading minus sign', () => {
    const points = [point('2026-08-01', '1.0000000000'), point('2026-08-02', '0.9000000000')];
    const change = computePeriodChange(points);
    expect(change?.absolute).toBe('-0.1');
    expect(change?.percent).toBe('-10.00%');
  });
});

describe('computePeriodHigh / computePeriodLow', () => {
  it('return null for an empty points array', () => {
    expect(computePeriodHigh([])).toBeNull();
    expect(computePeriodLow([])).toBeNull();
  });

  it('finds the max/min rates and formats them to six decimal places', () => {
    const points = [
      point('2026-08-01', '0.9000000000'),
      point('2026-08-02', '1.1000000000'),
      point('2026-08-03', '0.8500000000'),
    ];
    const high = computePeriodHigh(points);
    const low = computePeriodLow(points);
    expect(high).toEqual({ display: '1.100000', value: high?.value, date: '2026-08-02' });
    expect(low).toEqual({ display: '0.850000', value: low?.value, date: '2026-08-03' });
    expect(high?.value.toString()).toBe('1.1');
    expect(low?.value.toString()).toBe('0.85');
  });
});

describe('computeDailyChanges', () => {
  it('returns an empty array for an empty points array', () => {
    expect(computeDailyChanges([])).toEqual([]);
  });

  it('leaves the first (oldest) row with no percent value', () => {
    const points = [point('2026-08-01', '0.9000000000'), point('2026-08-02', '0.9900000000')];
    const changes = computeDailyChanges(points);
    expect(changes[0]).toEqual({ rateDate: '2026-08-01', percent: null });
    expect(changes[1]).toEqual({ rateDate: '2026-08-02', percent: '+10.00%' });
  });

  it('computes a signed daily percent change for every subsequent row', () => {
    const points = [
      point('2026-08-01', '1.0000000000'),
      point('2026-08-02', '1.1000000000'),
      point('2026-08-03', '1.0450000000'),
    ];
    const changes = computeDailyChanges(points);
    const second = changes[1];
    const third = changes[2];
    expect(second).toBeDefined();
    expect(third).toBeDefined();
    if (!second || !third) throw new Error('Expected daily changes for all three points');
    expect(second.percent).toBe('+10.00%');
    expect(third.percent).toBe('-5.00%');
  });

  it('uses exact decimal arithmetic with no floating-point drift (0.115% boundary case)', () => {
    // (1.00115 - 1) / 1 * 100 is exactly 0.115 in decimal math, which rounds half-up to
    // "0.12%". Plain JS `number` arithmetic computes this as 0.11499999999999844 (binary
    // float drift) and would wrongly round down to "0.11%" — this pins the Decimal-exact result.
    const points = [point('2026-08-01', '1.00000'), point('2026-08-02', '1.00115')];
    const changes = computeDailyChanges(points);
    const second = changes[1];
    expect(second).toBeDefined();
    if (!second) throw new Error('Expected a second daily change');
    expect(second.percent).toBe('+0.12%');

    const change = computePeriodChange(points);
    expect(change?.absolute).toBe('0.00115');
    expect(change?.percent).toBe('+0.12%');
  });
});
