import {
  Component,
  computed,
  forwardRef,
  input,
  model,
  OnChanges,
  SimpleChanges,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { CURRENCIES, type Currency } from './currencies';

const PAGE_SIZE = 30;
const SCROLL_LOAD_THRESHOLD_PX = 48;

@Component({
  selector: 'app-currency-combobox',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CurrencyCombobox),
      multi: true,
    },
  ],
  styleUrl: './currency-combobox.css',
  template: `
    <div class="relative flex flex-col gap-1">
      <label [for]="inputId()" class="text-sm font-medium text-gray-700">{{ label() }}</label>
      <input
        [id]="inputId()"
        [name]="name()"
        type="text"
        role="combobox"
        autocomplete="off"
        aria-autocomplete="list"
        class="rounded border border-gray-300 px-3 py-2"
        [class.rounded-b-none]="open()"
        [attr.aria-expanded]="open()"
        [attr.aria-controls]="listboxId()"
        [attr.aria-activedescendant]="activeOptionId()"
        [attr.aria-invalid]="ariaInvalid() ? 'true' : null"
        [attr.aria-describedby]="ariaDescribedBy() || null"
        [placeholder]="placeholder()"
        [value]="displayValue()"
        [disabled]="disabled()"
        (focus)="onFocus()"
        (input)="onInput($event)"
        (keydown)="onKeydown($event)"
        (blur)="onBlur()"
      />
      @if (open()) {
        <ul
          [id]="listboxId()"
          role="listbox"
          class="absolute top-full z-10 max-h-60 w-full min-w-64 overflow-y-auto rounded-b border border-t-0 border-gray-300 bg-white shadow-lg"
          (scroll)="onScroll($event)"
        >
          @for (currency of visible(); track currency.code; let i = $index) {
            <li
              [id]="optionId(i)"
              role="option"
              [attr.aria-selected]="currency.code === value()"
              [attr.data-code]="currency.code"
              class="cursor-pointer px-3 py-1.5 hover:bg-blue-50"
              [class.bg-blue-100]="i === activeIndex()"
              (mousedown)="onOptionMouseDown($event, currency)"
            >
              <span class="font-medium">{{ currency.code }}</span>
              <span class="text-gray-500"> — {{ currency.name }}</span>
            </li>
          } @empty {
            <li class="px-3 py-1.5 text-gray-500">No currencies match.</li>
          }
        </ul>
      }
    </div>
  `,
})
export class CurrencyCombobox implements ControlValueAccessor, OnChanges {
  readonly id = input.required<string>();
  readonly name = input<string>('');
  readonly label = input<string>('');
  readonly placeholder = input<string>('Search currency…');
  readonly ariaInvalid = input<boolean>(false);
  readonly ariaDescribedBy = input<string>('');
  readonly value = model<string>('');

  protected readonly query = signal('');
  protected readonly open = signal(false);
  protected readonly activeIndex = signal(-1);
  protected readonly visibleCount = signal(PAGE_SIZE);
  protected readonly disabled = signal(false);

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;
  private legacyBindingUsed = false;
  private formsApiUsed = false;

  protected readonly inputId = computed(() => `${this.id()}-input`);
  protected readonly listboxId = computed(() => `${this.id()}-listbox`);

  protected readonly filtered = computed<readonly Currency[]>(() => {
    const term = this.query().trim().toLowerCase();
    if (!term) {
      return CURRENCIES;
    }
    return CURRENCIES.filter(
      (currency) =>
        currency.code.toLowerCase().includes(term) || currency.name.toLowerCase().includes(term),
    );
  });

  protected readonly visible = computed(() => this.filtered().slice(0, this.visibleCount()));

  private readonly selected = computed<Currency | undefined>(() =>
    CURRENCIES.find((currency) => currency.code === this.value()),
  );

  protected readonly displayValue = computed(() => {
    if (this.open()) {
      return this.query();
    }
    const selected = this.selected();
    return selected ? `${selected.code} — ${selected.name}` : '';
  });

  protected readonly activeOptionId = computed<string | null>(() =>
    this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : null,
  );

  protected optionId(index: number): string {
    return `${this.id()}-option-${index}`;
  }

  protected onFocus(): void {
    if (this.disabled()) {
      return;
    }
    this.query.set('');
    this.visibleCount.set(PAGE_SIZE);
    this.activeIndex.set(-1);
    this.open.set(true);
  }

  protected onInput(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
    this.visibleCount.set(PAGE_SIZE);
    this.activeIndex.set(-1);
    this.open.set(true);
  }

  protected onScroll(event: Event): void {
    const target = event.target as HTMLElement;
    if (target.scrollTop + target.clientHeight >= target.scrollHeight - SCROLL_LOAD_THRESHOLD_PX) {
      this.loadMore();
    }
  }

  protected onKeydown(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown': {
        event.preventDefault();
        if (!this.open()) {
          this.onFocus();
          return;
        }
        const nextIndex = Math.min(this.activeIndex() + 1, this.visible().length - 1);
        this.activeIndex.set(nextIndex);
        if (nextIndex >= this.visible().length - 5) {
          this.loadMore();
        }
        break;
      }
      case 'ArrowUp':
        event.preventDefault();
        this.activeIndex.update((index) => Math.max(index - 1, 0));
        break;
      case 'Enter':
        if (this.open() && this.activeIndex() >= 0) {
          event.preventDefault();
          const currency = this.visible()[this.activeIndex()];
          if (currency) {
            this.select(currency);
          }
        }
        break;
      case 'Escape':
        this.open.set(false);
        this.query.set('');
        break;
    }
  }

  protected onOptionMouseDown(event: Event, currency: Currency): void {
    event.preventDefault();
    this.select(currency);
  }

  protected onBlur(): void {
    this.open.set(false);
    this.query.set('');
    this.onTouched();
  }

  writeValue(value: string | null): void {
    this.formsApiUsed = true;
    this.assertSingleBindingApi();
    this.value.set(value ?? '');
    this.query.set('');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.formsApiUsed = true;
    this.assertSingleBindingApi();
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.formsApiUsed = true;
    this.assertSingleBindingApi();
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
    if (isDisabled) {
      this.open.set(false);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['value']) {
      this.legacyBindingUsed = true;
      this.assertSingleBindingApi();
    }
  }

  private assertSingleBindingApi(): void {
    if (this.legacyBindingUsed && this.formsApiUsed) {
      throw new Error(
        'CurrencyCombobox cannot use value binding and Angular Forms on the same instance.',
      );
    }
  }

  private loadMore(): void {
    this.visibleCount.update((count) => Math.min(count + PAGE_SIZE, this.filtered().length));
  }

  private select(currency: Currency): void {
    this.value.set(currency.code);
    this.onChange(currency.code);
    this.query.set('');
    this.activeIndex.set(-1);
    this.open.set(false);
  }
}
