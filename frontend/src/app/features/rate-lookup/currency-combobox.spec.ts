import { TestBed } from '@angular/core/testing';
import { CurrencyCombobox } from './currency-combobox';
import { CURRENCIES } from './currencies';

describe('CurrencyCombobox', () => {
  function create() {
    const fixture = TestBed.createComponent(CurrencyCombobox);
    fixture.componentRef.setInput('id', 'from-currency');
    fixture.componentRef.setInput('name', 'from');
    fixture.componentRef.setInput('label', 'From');
    fixture.detectChanges();
    return fixture;
  }

  function input(fixture: ReturnType<typeof create>): HTMLInputElement {
    return fixture.nativeElement.querySelector('input[name="from"]');
  }

  function options(fixture: ReturnType<typeof create>): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('[role="option"]'));
  }

  it('renders only the first page of currencies on open, not the full list', () => {
    const fixture = create();

    input(fixture).dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    expect(CURRENCIES.length).toBeGreaterThan(30);
    expect(options(fixture).length).toBe(30);
  });

  it('loads another page when the listbox is scrolled near the bottom', () => {
    const fixture = create();

    input(fixture).dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    const listbox: HTMLElement = fixture.nativeElement.querySelector('[role="listbox"]');
    Object.defineProperty(listbox, 'scrollTop', { value: 1000, configurable: true });
    Object.defineProperty(listbox, 'clientHeight', { value: 200, configurable: true });
    Object.defineProperty(listbox, 'scrollHeight', { value: 1100, configurable: true });
    listbox.dispatchEvent(new Event('scroll'));
    fixture.detectChanges();

    expect(options(fixture).length).toBe(60);
  });

  it('filters by code or name as the user types, resetting to the first page', () => {
    const fixture = create();

    input(fixture).dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    input(fixture).value = 'dollar';
    input(fixture).dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const shown = options(fixture).map((el) => el.getAttribute('data-code'));
    expect(shown).toContain('USD');
    expect(shown).toContain('AUD');
    expect(shown).not.toContain('EUR');
  });

  it('selects a currency on mousedown and shows it in the input once closed', () => {
    const fixture = create();

    input(fixture).dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    input(fixture).value = 'USD';
    input(fixture).dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const option: HTMLElement = fixture.nativeElement.querySelector('[data-code="USD"]');
    option.dispatchEvent(new Event('mousedown'));
    fixture.detectChanges();

    expect(fixture.componentInstance.value()).toBe('USD');
    expect(input(fixture).value).toContain('USD');
    expect(fixture.nativeElement.querySelector('[role="listbox"]')).toBeNull();
  });
});
