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

function extremesPlugin(periodHigh: ExtremePoint | null, periodLow: ExtremePoint | null): Plugin<'line'> {
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
        ctx.font = '11px sans-serif';
        ctx.fillStyle = '#374151';
        ctx.textAlign = 'center';
        ctx.fillText(text, element.x, element.y + offsetY);
        ctx.restore();
      };

      annotate(periodHigh, 'High', -12);
      annotate(periodLow, 'Low', 20);
    },
  };
}

function buildConfig(
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
          borderColor: '#2563eb',
          backgroundColor: '#2563eb',
          borderWidth: 1.5,
          tension: 0.15,
          fill: false,
          pointRadius: (context) =>
            isExtremeIndex(context.dataIndex, periodHigh) || isExtremeIndex(context.dataIndex, periodLow)
              ? 5
              : 2,
          pointBackgroundColor: (context) =>
            isExtremeIndex(context.dataIndex, periodHigh)
              ? '#16a34a'
              : isExtremeIndex(context.dataIndex, periodLow)
                ? '#dc2626'
                : '#2563eb',
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        x: {
          type: 'category',
          grid: { color: '#f3f4f6' },
          ticks: {
            autoSkip: true,
            maxRotation: 0,
            maxTicksLimit: points.length <= DENSE_LABEL_THRESHOLD ? points.length : MAX_VISIBLE_TICKS,
          },
        },
        y: {
          beginAtZero: false,
          grid: { color: '#f3f4f6' },
        },
      },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            title: (items: TooltipItem<'line'>[]) => points[items[0]?.dataIndex ?? 0]?.rateDate ?? '',
            label: (item: TooltipItem<'line'>) => {
              const point = points[item.dataIndex];
              const change = dailyChanges[item.dataIndex]?.percent;
              return [`Rate: ${point.rate}`, `Daily change: ${change ?? '—'}`];
            },
          },
        },
      },
    },
    plugins: [extremesPlugin(periodHigh, periodLow)],
  };
}

@Component({
  selector: 'app-rate-trend-chart',
  template: `
    @if (points().length > 0) {
      <div class="relative h-72 w-full">
        <canvas #canvas></canvas>
      </div>
    } @else {
      <div
        class="flex h-72 items-center justify-center text-gray-500"
        data-testid="chart-no-data"
      >
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

      const config = buildConfig(points, dailyChanges, periodHigh, periodLow);
      this.chart?.destroy();
      this.chart = new Chart(canvas, config);
    });

    inject(DestroyRef).onDestroy(() => this.chart?.destroy());
  }
}
