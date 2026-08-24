import Decimal from 'decimal.js';

export const RATE_DECIMAL_PLACES = 6;

/** Formats an API decimal string for display without converting it to binary floating point. */
export function formatRate(value: string): string {
  return new Decimal(value).toFixed(RATE_DECIMAL_PLACES, Decimal.ROUND_HALF_UP);
}
