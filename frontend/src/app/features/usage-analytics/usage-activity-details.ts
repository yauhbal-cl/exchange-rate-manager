import { Component, HostListener, computed, input, output } from '@angular/core';
import { formatCount, type UsageTableRow } from './usage-metrics';

const WIDTH = 720;
const HEIGHT = 220;
const LEFT = 42;
const RIGHT = 12;
const TOP = 14;
const BOTTOM = 36;
const DATE_FORMATTER = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' });
const FULL_DATE_FORMATTER = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  timeZone: 'UTC',
});
const AVERAGE_FORMATTER = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 });

@Component({
  selector: 'app-usage-activity-details',
  host: { class: 'contents' },
  template: `
    <div
      class="fixed inset-0 z-50 grid place-items-center bg-[#101828]/45 px-4 py-8"
      role="presentation"
      (click)="closeFromBackdrop($event)"
      data-testid="activity-details-backdrop"
    >
      <section
        class="max-h-full w-full max-w-[880px] overflow-auto rounded-2xl border border-[var(--app-border)] bg-white shadow-[0_24px_64px_rgba(16,24,40,0.22)]"
        role="dialog"
        aria-modal="true"
        [attr.aria-labelledby]="headingId()"
        data-testid="activity-details-dialog"
      >
        <header
          class="flex items-start justify-between gap-5 border-b border-[var(--app-border)] px-6 py-5"
        >
          <div>
            <h2 class="m-0 text-xl font-[750] text-[#344054]" [id]="headingId()">
              {{ row().currencyCode }} activity
            </h2>
            <p class="mt-1.5 mb-0 text-[13px] text-[var(--app-muted)]">
              {{ formatCount(row().queriesInWindow) }} queries over the last {{ windowDays() }} days
            </p>
          </div>
          <button
            type="button"
            class="grid size-9 shrink-0 place-items-center rounded-lg border border-[var(--app-border)] bg-white text-lg leading-none text-[#667085] hover:bg-[#f9fafb] focus:outline-none focus:ring-2 focus:ring-[var(--app-accent-soft)]"
            aria-label="Close activity details"
            (click)="closed.emit()"
            data-testid="activity-details-close"
          >
            ×
          </button>
        </header>

        <div class="px-6 pt-6 pb-5">
          <div
            class="mb-5 flex flex-wrap items-center gap-x-8 gap-y-2 border-b border-dashed border-[var(--app-border)] pb-4 text-[13px]"
            data-testid="activity-details-statistics"
          >
            <p class="m-0 text-[var(--app-muted)]">
              Peak query day
              @if (row().busiestDay; as busiestDay) {
                <strong class="ml-1 text-[#344054]">
                  {{ formatDay(busiestDay.date) }} ·
                  {{ formatCount(busiestDay.queryCount) }}
                  {{ busiestDay.queryCount === 1 ? 'query' : 'queries' }}
                </strong>
              } @else {
                <strong class="ml-1 text-[#344054]">No activity</strong>
              }
            </p>
            <p class="m-0 text-[var(--app-muted)]">
              Average queries per day
              <strong class="ml-1 text-[#344054]">
                {{ formatAverage(row().averageQueriesPerDay) }} queries
              </strong>
            </p>
          </div>
          <svg
            class="block h-auto w-full"
            [attr.viewBox]="'0 0 ' + width + ' ' + height"
            role="img"
            [attr.aria-label]="chartLabel()"
            data-testid="detailed-activity-chart"
          >
            <text
              x="12"
              [attr.y]="height / 2"
              [attr.transform]="'rotate(-90 12 ' + height / 2 + ')'"
              text-anchor="middle"
              fill="#667085"
              font-size="11"
              font-weight="600"
              data-testid="detailed-chart-y-axis-label"
            >
              Queries
            </text>
            @for (tick of yTicks(); track tick.value) {
              <line
                [attr.x1]="left"
                [attr.y1]="tick.y"
                [attr.x2]="width - right"
                [attr.y2]="tick.y"
                stroke="#eaecf0"
                stroke-width="1"
              />
              <text
                [attr.x]="left - 9"
                [attr.y]="tick.y + 4"
                text-anchor="end"
                fill="#98a2b3"
                font-size="11"
              >
                {{ formatCount(tick.value) }}
              </text>
            }
            <path [attr.d]="areaPath()" fill="var(--app-accent-soft)" />
            <polyline
              [attr.points]="linePoints()"
              fill="none"
              stroke="var(--app-accent)"
              stroke-width="2.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            @for (point of points(); track $index) {
              <circle
                [attr.cx]="point.x"
                [attr.cy]="point.y"
                r="4"
                fill="white"
                stroke="var(--app-accent)"
                stroke-width="2"
                data-testid="detailed-activity-point"
                [attr.data-count]="point.count"
              >
                <title>{{ point.title }}</title>
              </circle>
              @if ($index % 2 === 0 || $last) {
                <text
                  [attr.x]="point.x"
                  [attr.y]="height - 10"
                  text-anchor="middle"
                  fill="#98a2b3"
                  font-size="10"
                >
                  {{ point.label }}
                </text>
              }
            }
          </svg>
        </div>
      </section>
    </div>
  `,
})
export class UsageActivityDetails {
  readonly row = input.required<UsageTableRow>();
  readonly windowDays = input.required<number>();
  readonly closed = output<void>();
  protected readonly formatCount = formatCount;
  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;
  protected readonly left = LEFT;
  protected readonly right = RIGHT;

  protected readonly headingId = computed(() => `activity-details-${this.row().currencyCode}`);
  protected readonly maximum = computed(() =>
    Math.max(1, ...this.row().activity.map((bucket) => bucket.count)),
  );
  protected readonly points = computed(() => {
    const activity = this.row().activity;
    const plotWidth = WIDTH - LEFT - RIGHT;
    const plotHeight = HEIGHT - TOP - BOTTOM;
    const step = activity.length > 1 ? plotWidth / (activity.length - 1) : 0;
    return activity.map((bucket, index) => ({
      x: LEFT + index * step,
      y: TOP + plotHeight - (bucket.count / this.maximum()) * plotHeight,
      count: bucket.count,
      label: DATE_FORMATTER.format(new Date(bucket.startsAt)),
      title: `${formatCount(bucket.count)} queries · ${DATE_FORMATTER.format(new Date(bucket.startsAt))}–${DATE_FORMATTER.format(new Date(bucket.endsAt))}`,
    }));
  });
  protected readonly linePoints = computed(() =>
    this.points()
      .map((point) => `${point.x},${point.y}`)
      .join(' '),
  );
  protected readonly areaPath = computed(() => {
    const points = this.points();
    const baseline = HEIGHT - BOTTOM;
    return points.length === 0
      ? ''
      : `M ${LEFT} ${baseline} L ${points.map((point) => `${point.x} ${point.y}`).join(' L ')} L ${WIDTH - RIGHT} ${baseline} Z`;
  });
  protected readonly yTicks = computed(() => {
    const maximum = this.maximum();
    const plotHeight = HEIGHT - TOP - BOTTOM;
    return [maximum, Math.round(maximum / 2), 0].map((value) => ({
      value,
      y: TOP + plotHeight - (value / maximum) * plotHeight,
    }));
  });
  protected readonly chartLabel = computed(
    () =>
      `Detailed activity for ${this.row().currencyCode}: ${formatCount(this.row().queriesInWindow)} queries over the last ${this.windowDays()} days`,
  );

  protected closeFromBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.closed.emit();
  }

  protected formatDay(date: string): string {
    return FULL_DATE_FORMATTER.format(new Date(`${date}T00:00:00Z`));
  }

  protected formatAverage(value: number): string {
    return AVERAGE_FORMATTER.format(value);
  }

  @HostListener('document:keydown.escape')
  protected closeFromEscape(): void {
    this.closed.emit();
  }
}
