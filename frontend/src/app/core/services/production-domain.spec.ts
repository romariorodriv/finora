import { environment as productionEnvironment } from '../../../environments/environment.prod';

describe('Production domain migration contract', () => {
  it('targets the new API origin and keeps a single /api/v1 prefix', () => {
    const base = new URL(productionEnvironment.apiBaseUrl);
    expect(productionEnvironment.production).toBeTrue();
    expect(base.protocol).toBe('https:');
    expect(base.origin).toBe('https://api.sarelaapp.com');
    expect(base.pathname).toBe('/api/v1');
    expect(base.search).toBe('');
    expect(base.hash).toBe('');
  });

  it('never uses the previous production backend as a fallback', () => {
    expect(productionEnvironment.apiBaseUrl).not.toContain('pgoapp.com');
    expect(productionEnvironment.apiBaseUrl.endsWith('/api/v1/api/v1')).toBeFalse();
  });
});
