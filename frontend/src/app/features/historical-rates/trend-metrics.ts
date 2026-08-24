import Decimal from 'decimal.js';
import type { RateTrendPoint } from '../../api-client';
import { formatRate } from '../../shared/rate-format';

export interface DecimalDisplay {
  display: string;
  value: Decimal;
}

export interface PeriodChange {
  absolute: string;
  percent: string;
  value: Decimal;
}

export interface ExtremePoint {
  display: string;
  value: Decimal;
  date: string;
}

export interface DailyChange {
  rateDate: string;
  percent: string | null;
}

/**
 * Formats a Decimal as a signed percentage string (e.g. "+2.40%", "-1.10%")
 * per data-model.md "Trend Metrics" — presentation only, never re-parsed.
 */
function formatPercent(value: Decimal): string {
  const sign = value.isNegative() ? '' : '+';
  return `${sign}${value.toFixed(2)}%`;
}

export function computeLatest(points: readonly RateTrendPoint[]): DecimalDisplay | null {
  if (points.length === 0) {
    return null;
  }
  const last = points.at(-1);
  if (!last) {
    return null;
  }
  return { display: formatRate(last.rate), value: new Decimal(last.rate) };
}

export function computePeriodChange(points: readonly RateTrendPoint[]): PeriodChange | null {
  if (points.length < 2) {
    return null;
  }
  const firstPoint = points[0];
  const lastPoint = points.at(-1);
  if (!firstPoint || !lastPoint) {
    return null;
  }
  const first = new Decimal(firstPoint.rate);
  const last = new Decimal(lastPoint.rate);
  const absolute = last.minus(first);
  const percent = first.isZero() ? new Decimal(0) : absolute.dividedBy(first).times(100);
  return { absolute: absolute.toString(), percent: formatPercent(percent), value: absolute };
}

export function computePeriodHigh(points: readonly RateTrendPoint[]): ExtremePoint | null {
  return extremePoint(points, (value, best) => value.greaterThan(best));
}

export function computePeriodLow(points: readonly RateTrendPoint[]): ExtremePoint | null {
  return extremePoint(points, (value, best) => value.lessThan(best));
}

function extremePoint(
  points: readonly RateTrendPoint[],
  isBetter: (value: Decimal, currentBest: Decimal) => boolean,
): ExtremePoint | null {
  let best: ExtremePoint | null = null;
  for (const point of points) {
    const value = new Decimal(point.rate);
    if (!best || isBetter(value, best.value)) {
      best = { display: formatRate(point.rate), value, date: point.rateDate };
    }
  }
  return best;
}

export function computeDailyChanges(points: readonly RateTrendPoint[]): DailyChange[] {
  return points.map((point, index) => {
    if (index === 0) {
      return { rateDate: point.rateDate, percent: null };
    }
    const previousPoint = points[index - 1];
    if (!previousPoint) {
      return { rateDate: point.rateDate, percent: null };
    }
    const previous = new Decimal(previousPoint.rate);
    const current = new Decimal(point.rate);
    const percent = previous.isZero()
      ? new Decimal(0)
      : current.minus(previous).dividedBy(previous).times(100);
    return { rateDate: point.rateDate, percent: formatPercent(percent) };
  });
}
