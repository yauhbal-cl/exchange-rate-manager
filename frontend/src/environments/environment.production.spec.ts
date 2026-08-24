import { environment } from './environment.production';

describe('production environment', () => {
  it('uses the versioned API base URL', () => {
    expect(environment.apiBaseUrl).toBe('/api/v1');
  });
});
