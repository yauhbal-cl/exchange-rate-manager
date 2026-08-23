import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Shell } from './shell';

@Component({ template: '' })
class StubView {}

describe('Shell', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'rate-lookup', component: StubView },
          { path: 'usage-analytics', component: StubView },
          { path: 'historical-rates', component: StubView },
        ]),
      ],
    });
  });

  it('marks only the active nav entry with aria-current="page"', async () => {
    const fixture = TestBed.createComponent(Shell);
    const router = TestBed.inject(Router);
    fixture.detectChanges();

    await router.navigateByUrl('/usage-analytics');
    fixture.detectChanges();

    const anchors: HTMLAnchorElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('a'),
    );
    const active = anchors.filter((a) => a.getAttribute('aria-current') === 'page');

    expect(active).toHaveLength(1);
    expect(active[0].getAttribute('href')).toBe('/usage-analytics');
  });

  it('omits the superseded standalone AI insight navigation entry', () => {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    const hrefs = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('a')).map(
      (anchor) => anchor.getAttribute('href'),
    );

    expect(hrefs).toEqual(['/rate-lookup', '/usage-analytics', '/historical-rates']);
  });
});
