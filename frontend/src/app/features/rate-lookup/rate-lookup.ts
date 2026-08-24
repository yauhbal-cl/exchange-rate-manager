import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { timeout } from 'rxjs';
import { ExchangeRateLookupService } from '../../api-client';
import { formatIsoDateUtc, todayIsoUtc } from '../../shared/date-utils';
import { problemDetail } from '../../shared/problem-detail';
import { STANDARD_BACKEND_TIMEOUT_MS } from '../../shared/http-policy';
import { CurrencyCombobox } from './currency-combobox';
import {
  RateLookupResult,
  type LookupError,
  type RateLookupResultState,
} from './rate-lookup-result';

interface RateLookupRequest {
  from: string;
  to: string;
  date: string | undefined;
}
const UNREACHABLE_MESSAGE = 'Unable to reach the exchange rate service. Please try again later.';

function differentCurrencies(control: AbstractControl): ValidationErrors | null {
  const from = control.get('from')?.value;
  const to = control.get('to')?.value;
  return from && to && from === to ? { sameCurrency: true } : null;
}

function maximumDate(maximum: string): ValidatorFn {
  return (control) =>
    typeof control.value === 'string' && control.value.length > 0 && control.value > maximum
      ? { futureDate: true }
      : null;
}

export function categorizeLookupError(error: unknown): LookupError {
  if (error instanceof HttpErrorResponse) {
    const detail = problemDetail(error.error);
    if (error.status === 400)
      return { category: 'invalid', message: detail ?? 'The lookup request is invalid.' };
    if (error.status === 404)
      return {
        category: 'no-data',
        message: detail ?? 'No stored rate was found for this pair and date.',
      };
    if (error.status === 0 || error.status >= 500)
      return { category: 'unreachable', message: UNREACHABLE_MESSAGE };
  }
  return { category: 'unreachable', message: UNREACHABLE_MESSAGE };
}

@Component({
  selector: 'app-rate-lookup',
  imports: [ReactiveFormsModule, CurrencyCombobox, RateLookupResult],
  host: {
    class:
      'block min-h-[calc(100vh-57px)] bg-[var(--app-page-bg)] text-[var(--app-text)] tabular-nums',
  },
  templateUrl: './rate-lookup.html',
})
export class RateLookup {
  private readonly service = inject(ExchangeRateLookupService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly today = todayIsoUtc();
  protected readonly formattedToday = formatIsoDateUtc(this.today);
  protected readonly submitted = signal(false);
  protected readonly submittedRequest = signal<RateLookupRequest | undefined>(undefined);
  protected readonly form = this.formBuilder.group(
    {
      from: ['', Validators.required],
      to: ['', Validators.required],
      date: ['', maximumDate(this.today)],
    },
    { validators: differentCurrencies },
  );

  protected readonly rate = rxResource({
    params: () => this.submittedRequest(),
    stream: ({ params }) =>
      this.service
        .getExchangeRate(params.from, params.to, params.date)
        .pipe(timeout({ each: STANDARD_BACKEND_TIMEOUT_MS })),
  });

  protected readonly resultState = computed<RateLookupResultState>(() => {
    if (this.rate.isLoading()) return { kind: 'loading' };
    const error = this.rate.error();
    if (error !== undefined) return { kind: 'error', error: categorizeLookupError(error) };
    if (this.rate.hasValue()) return { kind: 'success', value: this.rate.value() };
    return { kind: 'initial' };
  });

  constructor() {
    effect(() => {
      if (this.rate.isLoading()) this.form.disable({ emitEvent: false });
      else this.form.enable({ emitEvent: false });
    });
  }

  protected showError(control: AbstractControl): boolean {
    return control.invalid && (control.touched || control.dirty || this.submitted());
  }

  protected showPairError(): boolean {
    return (
      this.form.hasError('sameCurrency') &&
      (this.submitted() || this.form.controls.from.touched || this.form.controls.to.touched)
    );
  }

  protected showRequiredSummary(): boolean {
    return (
      this.submitted() &&
      (this.form.controls.from.hasError('required') || this.form.controls.to.hasError('required'))
    );
  }

  protected onDateChange(event: Event): void {
    if (event.target instanceof HTMLInputElement) {
      this.form.controls.date.setValue(event.target.value);
      this.form.controls.date.markAsDirty();
    }
  }

  protected onSubmit(): void {
    this.submitted.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.submittedRequest.set({ from: value.from, to: value.to, date: value.date || undefined });
  }

  protected swapCurrencies(): void {
    const { from, to } = this.form.getRawValue();
    this.form.patchValue({ from: to, to: from });
    this.form.markAsDirty();
  }
}
