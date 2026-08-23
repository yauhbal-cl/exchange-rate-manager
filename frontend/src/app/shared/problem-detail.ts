import type { ProblemDetail } from '../api-client';

export function problemDetail(errorBody: unknown): string | null {
  if (!errorBody || typeof errorBody !== 'object') {
    return null;
  }
  const detail = (errorBody as ProblemDetail).detail;
  return typeof detail === 'string' && detail.trim().length > 0 ? detail.trim() : null;
}
