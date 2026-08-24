import { Component, computed, input } from '@angular/core';
import { formatCount, type ActivityBucket } from './usage-metrics';

const CHART_WIDTH = 240;
const CHART_HEIGHT = 44;
const CHART_TOP = 4;
const CHART_BOTTOM = 40;
const BUCKET_TIME_FORMATTER = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: 'numeric',
});

interface ChartPoint {
  x: number;
  y: number;
  title: string;
}

@Component({
  selector: 'app-usage-activity-chart',
  host: { class: 'block min-w-[220px]' },
  template: `
    <svg
      class="block h-11 w-full overflow-visible"
      [attr.viewBox]="'0 0 ' + chartWidth + ' ' + chartHeight"
      role="img"
      [attr.aria-label]="accessibleLabel()"
      preserveAspectRatio="none"
      data-testid="activity-chart"
    >
      <line
        x1="0"
        [attr.y1]="chartBottom"
        [attr.x2]="chartWidth"
        [attr.y2]="chartBottom"
        stroke="#e4e7ec"
        stroke-width="1"
        vector-effect="non-scaling-stroke"
      />
      <path [attr.d]="areaPath()" fill="var(--app-accent-soft)" />
      <polyline
        [attr.points]="linePoints()"
        fill="none"
        stroke="var(--app-accent)"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        vector-effect="non-scaling-stroke"
      />
      @for (point of points(); track $index) {
        <circle
          [attr.cx]="point.x"
          [attr.cy]="point.y"
          r="5"
          fill="transparent"
          data-testid="activity-point"
          [attr.data-count]="activity()[$index]?.count ?? 0"
        >
          <title>{{ point.title }}</title>
        </circle>
      }
    </svg>
  `,
})
export class UsageActivityChart {
  readonly activity = input<readonly ActivityBucket[]>([]);
  readonly currencyCode = input.required<string>();
  readonly windowDays = input.required<number>();
  readonly queriesInWindow = input.required<number>();

  protected readonly chartWidth = CHART_WIDTH;
  protected readonly chartHeight = CHART_HEIGHT;
  protected readonly chartBottom = CHART_BOTTOM;

  protected readonly points = computed<ChartPoint[]>(() => {
    const buckets = this.activity();
    const step = buckets.length > 1 ? CHART_WIDTH / (buckets.length - 1) : 0;

    return buckets.map((bucket, index) => ({
      x: index * step,
      y: CHART_BOTTOM - (bucket.heightPercent / 100) * Math.max(0, CHART_BOTTOM - CHART_TOP),
      title: `${formatCount(bucket.count)} queries · ${BUCKET_TIME_FORMATTER.format(new Date(bucket.startsAt))}–${BUCKET_TIME_FORMATTER.format(new Date(bucket.endsAt))}`,
    }));
  });

  protected readonly linePoints = computed(() =>
    this.points()
      .map((point) => `${point.x},${point.y}`)
      .join(' '),
  );

  protected readonly areaPath = computed(() => {
    const points = this.points();
    if (points.length === 0) {
      return '';
    }
    return `M 0 ${CHART_BOTTOM} L ${points.map((point) => `${point.x} ${point.y}`).join(' L ')} L ${CHART_WIDTH} ${CHART_BOTTOM} Z`;
  });

  protected readonly accessibleLabel = computed(
    () =>
      `${formatCount(this.queriesInWindow())} queries for ${this.currencyCode()} over the last ${this.windowDays()} days`,
  );
}
