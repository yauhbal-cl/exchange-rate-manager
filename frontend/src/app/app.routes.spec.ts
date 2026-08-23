import { NotFound } from './not-found/not-found';
import { routes } from './app.routes';

describe('app.routes', () => {
  it('redirects the empty path to rate-lookup', () => {
    const emptyRoute = routes.find((route) => route.path === '');

    expect(emptyRoute?.pathMatch).toBe('full');
    expect(emptyRoute?.redirectTo).toBe('rate-lookup');
  });

  it('does not expose the superseded standalone AI insight route', () => {
    expect(routes.some((route) => route.path === 'ai-insight')).toBe(false);
  });

  it('resolves the wildcard path to the not-found view', async () => {
    const wildcardRoute = routes.find((route) => route.path === '**');

    expect(wildcardRoute).toBeTruthy();
    const component = await wildcardRoute!.loadComponent!();
    expect(component).toBe(NotFound);
  });
});
