import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { RateLookup } from './rate-lookup';
import { ExchangeRateLookupService, type ExchangeRateResponse } from '../../api-client';

function selectCurrency(
  fixture: { nativeElement: HTMLElement; detectChanges(): void },
  name: string,
  code: string,
): void {
  const input: HTMLInputElement = fixture.nativeElement.querySelector(`input[name="${name}"]`)!;
  input.dispatchEvent(new Event('focus'));
  fixture.detectChanges();
  input.value = code;
  input.dispatchEvent(new Event('input'));
  fixture.detectChanges();
  const option: HTMLElement = fixture.nativeElement.querySelector(`[data-code="${code}"]`)!;
  option.dispatchEvent(new Event('mousedown'));
  fixture.detectChanges();
}

async function flush(fixture: {
  whenStable(): Promise<unknown>;
  detectChanges(): void;
}): Promise<void> {
  await fixture.whenStable();
  fixture.detectChanges();
}

function response(overrides: Partial<ExchangeRateResponse> = {}): ExchangeRateResponse {
  return {
    fromCurrency: 'USD',
    toCurrency: 'EUR',
    rate: '0.9234500000',
    rateDate: '2026-08-20',
    fromCurrencyUsageCount: 3,
    toCurrencyUsageCount: 5,
    ...overrides,
  };
}

describe('RateLookup', () => {
  let getExchangeRate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    getExchangeRate = vi.fn();
    TestBed.configureTestingModule({
      providers: [{ provide: ExchangeRateLookupService, useValue: { getExchangeRate } }],
    });
  });

  it('submits the selected currency pair and renders the result', async () => {
    getExchangeRate.mockReturnValue(of(response()));

    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'EUR');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('button[type="submit"]').click();
    await flush(fixture);

    expect(getExchangeRate).toHaveBeenCalledTimes(1);
    expect(getExchangeRate).toHaveBeenCalledWith('USD', 'EUR', undefined);

    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('USD');
    expect(text).toContain('EUR');
    expect(text).toContain('0.923450');
    expect(text).not.toContain('0.9234500000');
    expect(text).toContain('2026-08-20');
    expect(text).toContain('3');
    expect(text).toContain('5');
  });

  it('replaces the previous result rather than appending when resubmitted with a changed target', async () => {
    getExchangeRate
      .mockReturnValueOnce(of(response()))
      .mockReturnValueOnce(
        of(response({ toCurrency: 'GBP', rate: '0.7800000000', rateDate: '2026-08-21' })),
      );

    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    const submit = () => fixture.nativeElement.querySelector('button[type="submit"]').click();

    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'EUR');
    fixture.detectChanges();
    submit();
    await flush(fixture);

    selectCurrency(fixture, 'to', 'GBP');
    fixture.detectChanges();
    submit();
    await flush(fixture);

    expect(getExchangeRate).toHaveBeenCalledTimes(2);

    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('GBP');
    expect(text).not.toContain('0.9234500000');
  });

  it('passes a selected date through to getExchangeRate', () => {
    getExchangeRate.mockReturnValue(of(response({ rateDate: '2026-01-15' })));

    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    const dateInput: HTMLInputElement = fixture.nativeElement.querySelector('input[name="date"]');
    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'EUR');
    dateInput.value = '2026-01-15';
    dateInput.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('button[type="submit"]').click();
    fixture.detectChanges();

    expect(getExchangeRate).toHaveBeenCalledWith('USD', 'EUR', '2026-01-15');
  });

  it('omits the date when the date field is left blank', () => {
    getExchangeRate.mockReturnValue(of(response()));

    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'EUR');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('button[type="submit"]').click();
    fixture.detectChanges();

    expect(getExchangeRate).toHaveBeenCalledWith('USD', 'EUR', undefined);
  });

  it('shows a validation message and blocks submit when currencies are identical', () => {
    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'USD');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('button[type="submit"]').click();
    fixture.detectChanges();

    expect(getExchangeRate).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      'Source and target currency must be different.',
    );
  });

  it('shows a validation message and blocks submit when a currency is unselected', () => {
    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    selectCurrency(fixture, 'from', 'USD');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('button[type="submit"]').click();
    fixture.detectChanges();

    expect(getExchangeRate).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      'Select both a source and a target currency.',
    );
  });

  it('top-aligns currency fields so a field error does not move the other input', () => {
    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    const currencyRow: HTMLElement = fixture.nativeElement.querySelector('.currency-row');
    const swapButton: HTMLElement = fixture.nativeElement.querySelector('.swap-button');

    expect(currencyRow.classList).toContain('items-start');
    expect(currencyRow.classList).not.toContain('items-end');
    expect(swapButton.classList).toContain('mt-[22px]');
  });

  it('shows a validation message and blocks submit when the date is after today', () => {
    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    const dateInput: HTMLInputElement = fixture.nativeElement.querySelector('input[name="date"]');
    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'EUR');
    dateInput.value = '2099-01-01';
    dateInput.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('button[type="submit"]').click();
    fixture.detectChanges();

    expect(getExchangeRate).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Date cannot be in the future.');
  });

  it('renders the invalid category message from a 400 response', async () => {
    getExchangeRate.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 400, error: { detail: 'from and to must differ.' } }),
      ),
    );

    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'EUR');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('button[type="submit"]').click();
    await flush(fixture);

    expect(fixture.nativeElement.textContent).toContain('from and to must differ.');
  });

  it('renders the no-data category message from a 404 response', async () => {
    getExchangeRate.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { detail: 'No stored rate for that date.' },
          }),
      ),
    );

    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'EUR');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('button[type="submit"]').click();
    await flush(fixture);

    expect(fixture.nativeElement.textContent).toContain('No stored rate for that date.');
  });

  it('renders the unreachable fallback message on a network failure, leaving the form usable', async () => {
    getExchangeRate.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'EUR');
    fixture.detectChanges();

    const submit: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    submit.click();
    await flush(fixture);

    expect(fixture.nativeElement.textContent).toContain(
      'Unable to reach the exchange rate service. Please try again later.',
    );
    expect(submit.disabled).toBe(false);
  });

  it('clears the previous error once a retried submit resolves', async () => {
    getExchangeRate
      .mockReturnValueOnce(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 404,
              error: { detail: 'No rate data found for that date.' },
            }),
        ),
      )
      .mockReturnValueOnce(of(response()));

    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    const submit = () => fixture.nativeElement.querySelector('button[type="submit"]').click();
    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'EUR');
    fixture.detectChanges();

    submit();
    await flush(fixture);
    expect(fixture.nativeElement.textContent).toContain('No rate data found for that date.');

    submit();
    await flush(fixture);

    const text: string = fixture.nativeElement.textContent;
    expect(text).not.toContain('No rate data found for that date.');
    expect(text).toContain('0.923450');
  });

  it('discards a stale response when a newer request resolves first', async () => {
    // Per FR-005/edge cases, the submit control is disabled while a lookup is in flight,
    // so this can't be driven through the disabled button — it exercises the rxResource
    // request-identity discard (FR-010) that backstops that same-tab race directly.
    const first = new Subject<ExchangeRateResponse>();
    const second = new Subject<ExchangeRateResponse>();
    getExchangeRate.mockReturnValueOnce(first).mockReturnValueOnce(second);

    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();

    const submitButton: HTMLButtonElement =
      fixture.nativeElement.querySelector('button[type="submit"]');
    const submit = () => {
      submitButton.disabled = false;
      submitButton.click();
    };

    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'EUR');
    fixture.detectChanges();
    submit();
    fixture.detectChanges();

    selectCurrency(fixture, 'to', 'GBP');
    fixture.detectChanges();
    submit();
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();

    second.next(response({ toCurrency: 'GBP', rate: '0.5000000000' }));
    second.complete();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();

    first.next(response({ toCurrency: 'EUR', rate: '0.6600000000' }));
    first.complete();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();

    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('0.500000');
    expect(text).not.toContain('0.660000');
  });

  it('uses status-specific fallbacks when a problem detail is absent or blank', async () => {
    getExchangeRate
      .mockReturnValueOnce(
        throwError(() => new HttpErrorResponse({ status: 400, error: { detail: '   ' } })),
      )
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404, error: {} })));
    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();
    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'EUR');

    const submit = () => fixture.nativeElement.querySelector('button[type="submit"]').click();
    submit();
    await flush(fixture);
    expect(fixture.nativeElement.textContent).toContain('The lookup request is invalid.');

    submit();
    await flush(fixture);
    expect(fixture.nativeElement.textContent).toContain(
      'No stored rate was found for this pair and date.',
    );
  });

  it('categorizes a server failure as unreachable even when it includes detail copy', async () => {
    getExchangeRate.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503, error: { detail: 'internal' } })),
    );
    const fixture = TestBed.createComponent(RateLookup);
    fixture.detectChanges();
    selectCurrency(fixture, 'from', 'USD');
    selectCurrency(fixture, 'to', 'EUR');
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    await flush(fixture);
    expect(fixture.nativeElement.textContent).toContain(
      'Unable to reach the exchange rate service.',
    );
    expect(fixture.nativeElement.textContent).not.toContain('internal');
  });

  it('times out after 10 seconds and re-enables the form', async () => {
    vi.useFakeTimers();
    try {
      getExchangeRate.mockReturnValue(new Subject<ExchangeRateResponse>());
      const fixture = TestBed.createComponent(RateLookup);
      fixture.detectChanges();
      selectCurrency(fixture, 'from', 'USD');
      selectCurrency(fixture, 'to', 'EUR');
      const submit: HTMLButtonElement =
        fixture.nativeElement.querySelector('button[type="submit"]');
      submit.click();
      fixture.detectChanges();
      expect(submit.disabled).toBe(true);

      await vi.advanceTimersByTimeAsync(10_001);
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain(
        'Unable to reach the exchange rate service.',
      );
      expect(submit.disabled).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });
});
