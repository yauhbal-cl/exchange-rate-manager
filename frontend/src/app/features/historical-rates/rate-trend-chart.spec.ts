import type { Chart, ScriptableContext, TooltipItem } from 'chart.js';
import type { RateTrendPoint } from '../../api-client';
import { buildExtremesPlugin, buildTrendChartConfig } from './rate-trend-chart';
import { computeDailyChanges, computePeriodHigh, computePeriodLow } from './trend-metrics';

const points: readonly RateTrendPoint[] = [
  { rateDate: '2026-08-01', rate: '1.0000000000' },
  { rateDate: '2026-08-02', rate: '1.1000000000' },
  { rateDate: '2026-08-03', rate: '0.9900000000' },
];

describe('rate trend chart configuration', () => {
  it('converts decimal strings only at the dataset boundary and applies scale geometry', () => {
    const config = buildTrendChartConfig(
      points,
      computeDailyChanges(points),
      computePeriodHigh(points),
      computePeriodLow(points),
    );
    const dataset = config.data.datasets[0];
    expect(dataset).toBeDefined();
    if (!dataset) throw new Error('Expected a line dataset');
    expect(dataset.data).toEqual([1, 1.1, 0.99]);

    const x = config.options?.scales?.['x'];
    const y = config.options?.scales?.['y'];
    expect(x?.offset).toBe(true);
    expect(x?.ticks?.maxTicksLimit).toBe(3);
    expect((y as { grace?: string } | undefined)?.grace).toBe('15%');
  });

  it('caps dense date labels at eight ticks', () => {
    const dense = Array.from({ length: 20 }, (_, index) => ({
      rateDate: `2026-08-${String(index + 1).padStart(2, '0')}`,
      rate: String(index + 1),
    }));
    const config = buildTrendChartConfig(
      dense,
      computeDailyChanges(dense),
      computePeriodHigh(dense),
      computePeriodLow(dense),
    );
    expect(config.options?.scales?.['x']?.ticks?.maxTicksLimit).toBe(8);
  });

  it('builds tooltip date, six-decimal rate, and daily-change content safely', () => {
    const config = buildTrendChartConfig(
      points,
      computeDailyChanges(points),
      computePeriodHigh(points),
      computePeriodLow(points),
    );
    const callbacks = config.options?.plugins?.tooltip?.callbacks;
    const title = callbacks?.title as unknown as (items: TooltipItem<'line'>[]) => string;
    const label = callbacks?.label as unknown as (item: TooltipItem<'line'>) => string[];
    expect(title([{ dataIndex: 1 } as TooltipItem<'line'>])).toBe('2026-08-02');
    expect(label({ dataIndex: 1 } as TooltipItem<'line'>)).toEqual([
      'Rate: 1.100000',
      'Daily change: +10.00%',
    ]);
  });

  it('marks high and low points and annotates their coordinates', () => {
    const high = computePeriodHigh(points);
    const low = computePeriodLow(points);
    const config = buildTrendChartConfig(points, computeDailyChanges(points), high, low);
    const dataset = config.data.datasets[0];
    expect(dataset).toBeDefined();
    if (!dataset) throw new Error('Expected a line dataset');
    const radius = dataset.pointRadius as (context: ScriptableContext<'line'>) => number;
    expect(radius({ dataIndex: 1 } as ScriptableContext<'line'>)).toBe(4.5);
    expect(radius({ dataIndex: 2 } as ScriptableContext<'line'>)).toBe(4.5);
    expect(radius({ dataIndex: 0 } as ScriptableContext<'line'>)).toBe(0);

    const fillText = vi.fn();
    const plugin = buildExtremesPlugin(high, low);
    const hook = plugin.afterDatasetsDraw as unknown as (chart: Chart<'line'>) => void;
    hook({
      data: { labels: points.map((point) => point.rateDate) },
      getDatasetMeta: () => ({
        data: [
          { x: 10, y: 30 },
          { x: 20, y: 15 },
          { x: 30, y: 40 },
        ],
      }),
      ctx: { save: vi.fn(), restore: vi.fn(), fillText },
    } as unknown as Chart<'line'>);
    expect(fillText).toHaveBeenCalledWith('High', 20, 3);
    expect(fillText).toHaveBeenCalledWith('Low', 30, 60);
  });
});
