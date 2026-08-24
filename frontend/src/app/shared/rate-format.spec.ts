import { formatRate } from './rate-format';

describe('formatRate', () => {
  it('shows exactly six decimal places', () => {
    expect(formatRate('1.2')).toBe('1.200000');
    expect(formatRate('0.923450000000')).toBe('0.923450');
  });

  it('rounds half up without binary floating-point drift', () => {
    expect(formatRate('1.2345675')).toBe('1.234568');
    expect(formatRate('999999.9999995')).toBe('1000000.000000');
  });
});
