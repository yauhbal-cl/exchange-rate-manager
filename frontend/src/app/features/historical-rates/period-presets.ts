export type PresetId = '7D' | '15D' | '1M' | '3M' | '6M';

export interface PeriodPreset {
  id: PresetId;
  label: string;
  unit: 'days' | 'months';
  amount: number;
}

export const PERIOD_PRESETS: readonly PeriodPreset[] = [
  { id: '7D', label: '7D', unit: 'days', amount: 7 },
  { id: '15D', label: '15D', unit: 'days', amount: 15 },
  { id: '1M', label: '1M', unit: 'months', amount: 1 },
  { id: '3M', label: '3M', unit: 'months', amount: 3 },
  { id: '6M', label: '6M', unit: 'months', amount: 6 },
];

export type PeriodSelection =
  | { kind: 'preset'; id: PresetId }
  | { kind: 'custom'; startDate: string; endDate: string };

export interface DateRange {
  startDate: string;
  endDate: string;
}

const CUSTOM_RANGE_MAX_MONTHS = 6;

export function todayIso(): string {
  return toIso(new Date());
}

/**
 * Subtracts calendar months from a yyyy-MM-dd date, clamping the day to the
 * target month's length (e.g. 2026-08-31 minus 1 month -> 2026-07-31,
 * 2026-03-31 minus 1 month -> 2026-02-28).
 */
export function subtractMonths(date: string, months: number): string {
  const [year, month, day] = parseIso(date);
  const totalMonths = year * 12 + (month - 1) - months;
  const targetYear = Math.floor(totalMonths / 12);
  const targetMonthIndex = totalMonths - targetYear * 12;
  const clampedDay = Math.min(day, daysInMonth(targetYear, targetMonthIndex + 1));
  return formatIso(targetYear, targetMonthIndex + 1, clampedDay);
}

export function subtractDays(date: string, days: number): string {
  const [year, month, day] = parseIso(date);
  const utcDate = new Date(Date.UTC(year, month - 1, day));
  utcDate.setUTCDate(utcDate.getUTCDate() - days);
  return toIso(utcDate);
}

export function resolveRange(selection: PeriodSelection, today: string): DateRange {
  if (selection.kind === 'custom') {
    return { startDate: selection.startDate, endDate: selection.endDate };
  }
  const preset = PERIOD_PRESETS.find((candidate) => candidate.id === selection.id)!;
  const endDate = today;
  const startDate =
    preset.unit === 'days'
      ? subtractDays(today, preset.amount)
      : subtractMonths(today, preset.amount);
  return { startDate, endDate };
}

/**
 * Validates a custom range against FR-006: start must not be after end, and
 * the span must not exceed 6 months — the same "6 months" boundary the `6M`
 * preset resolves against, via `subtractMonths`.
 */
export function customRangeError(startDate: string, endDate: string): string | null {
  if (endDate < startDate) {
    return 'End date must be on or after the start date.';
  }
  if (startDate < subtractMonths(endDate, CUSTOM_RANGE_MAX_MONTHS)) {
    return 'Custom range cannot exceed 6 months.';
  }
  return null;
}

function parseIso(date: string): [number, number, number] {
  const [year, month, day] = date.split('-').map(Number);
  return [year, month, day];
}

function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

function formatIso(year: number, month: number, day: number): string {
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function toIso(date: Date): string {
  return date.toISOString().slice(0, 10);
}
