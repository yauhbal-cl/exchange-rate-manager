export function todayIsoUtc(now = new Date()): string {
  return now.toISOString().slice(0, 10);
}

export function formatIsoDateUtc(value: string | undefined): string {
  if (!value) {
    return '—';
  }
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${value}T00:00:00Z`));
}
