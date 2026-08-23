import {
  Component,
  DestroyRef,
  ElementRef,
  afterRenderEffect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import {
  Chart,
  type ChartConfiguration,
  type Plugin,
  type TooltipItem,
  registerables,
} from 'chart.js';
import type { RateTrendPoint } from '../../api-client';
import type { DailyChange, ExtremePoint } from './trend-metrics';

Chart.register(...registerables);

const DENSE_LABEL_THRESHOLD = 15;
const MAX_VISIBLE_TICKS = 8;

export function buildExtremesPlugin(
  periodHigh: ExtremePoint | null,
  periodLow: ExtremePoint | null,
): Plugin<'line'> {
  return {
    id: 'periodExtremes',
    afterDatasetsDraw(chart) {
      const meta = chart.getDatasetMeta(0);
      const labels = chart.data.labels as string[];
      const ctx = chart.ctx;

      const annotate = (point: ExtremePoint | null, text: string, offsetY: number) => {
        if (!point) {
          return;
        }
        const index = labels.indexOf(point.date);
        const element = index >= 0 ? meta.data[index] : undefined;
        if (!element) {
          return;
        }
        ctx.save();
        ctx.font = '700 11px Inter, system-ui, sans-serif';
        ctx.fillStyle = '#344054';
        ctx.textAlign = 'center';
        ctx.fillText(text, element.x, element.y + offsetY);
        ctx.restore();
      };

      annotate(periodHigh, 'High', -12);
      annotate(periodLow, 'Low', 20);
    },
  };
}

export function buildTrendChartConfig(
  points: readonly RateTrendPoint[],
  dailyChanges: readonly DailyChange[],
  periodHigh: ExtremePoint | null,
  periodLow: ExtremePoint | null,
): ChartConfiguration<'line'> {
  const labels = points.map((point) => point.rateDate);
  const isExtremeIndex = (index: number, extreme: ExtremePoint | null) =>
    extreme !== null && points[index]?.rateDate === extreme.date;

  return {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          data: points.map((point) => Number(point.rate)),
          borderColor: '#344054',
          backgroundColor: '#344054',
          borderWidth: 2.2,
          tension: 0.18,
          fill: false,
          pointRadius: (context) =>
            isExtremeIndex(context.dataIndex, periodHigh) ||
            isExtremeIndex(context.dataIndex, periodLow)
              ? 4.5
              : 0,
          pointBackgroundColor: (context) =>
            isExtremeIndex(context.dataIndex, periodHigh)
              ? '#ffffff'
              : isExtremeIndex(context.dataIndex, periodLow)
                ? '#ffffff'
                : '#344054',
          pointBorderColor: '#344054',
          pointBorderWidth: 2,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: '#ffffff',
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        x: {
          type: 'category',
          offset: true,
          border: { display: false },
          grid: { display: false },
          ticks: {
            color: '#98a2b3',
            font: { size: 11 },
            autoSkip: true,
            maxRotation: 0,
            maxTicksLimit:
              points.length <= DENSE_LABEL_THRESHOLD ? points.length : MAX_VISIBLE_TICKS,
          },
        },
        y: {
          beginAtZero: false,
          grace: '15%',
          border: { display: false },
          grid: { color: '#eef1f4' },
          ticks: { color: '#98a2b3', font: { size: 11 }, padding: 8 },
        },
      },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#111827',
          titleFont: { size: 13, weight: 700 },
          bodyFont: { size: 12 },
          padding: 10,
          cornerRadius: 10,
          displayColors: false,
          callbacks: {
            title: (items: TooltipItem<'line'>[]) =>
              points[items[0]?.dataIndex ?? 0]?.rateDate ?? '',
            label: (item: TooltipItem<'line'>) => {
              const point = points[item.dataIndex];
              if (!point) {
                return [];
              }
              const change = dailyChanges[item.dataIndex]?.percent;
              return [`Rate: ${point.rate}`, `Daily change: ${change ?? '—'}`];
            },
          },
        },
      },
    },
    plugins: [buildExtremesPlugin(periodHigh, periodLow)],
  };
}

@Component({
  selector: 'app-rate-trend-chart',
  styleUrl: './rate-trend-chart.css',
  template: `
    @if (points().length > 0) {
      <div class="chart-container">
        <canvas #canvas role="img" [attr.aria-label]="accessibleLabel()"></canvas>
      </div>
    } @else {
      <div class="chart-empty" data-testid="chart-no-data">
        No historical rate data for this pair and period.
      </div>
    }
  `,
})
export class RateTrendChart {
  readonly points = input<readonly RateTrendPoint[]>([]);
  readonly dailyChanges = input<readonly DailyChange[]>([]);
  readonly periodHigh = input<ExtremePoint | null>(null);
  readonly periodLow = input<ExtremePoint | null>(null);
  readonly accessibleLabel = input<string>(
    'Exchange-rate trend. The historical rates table provides the same data as text.',
  );

  private readonly canvasRef = viewChild<ElementRef<HTMLCanvasElement>>('canvas');
  private chart: Chart<'line'> | undefined;

  constructor() {
    afterRenderEffect(() => {
      const points = this.points();
      const dailyChanges = this.dailyChanges();
      const periodHigh = this.periodHigh();
      const periodLow = this.periodLow();
      const canvas = this.canvasRef()?.nativeElement;

      if (!canvas || points.length === 0) {
        this.chart?.destroy();
        this.chart = undefined;
        return;
      }

      const config = buildTrendChartConfig(points, dailyChanges, periodHigh, periodLow);
      this.chart?.destroy();
      this.chart = new Chart(canvas, config);
    });

    inject(DestroyRef).onDestroy(() => this.chart?.destroy());
  }
}
