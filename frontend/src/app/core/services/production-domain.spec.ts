import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { environment as production } from '../../../environments/environment.prod';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { AdminService } from '../../admin/admin.service';

describe('Sarela domain migration contract', () => {
  let controller: HttpTestingController;
  let api: ApiService;
  let auth: AuthService;
  let admin: AdminService;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()]
    });
    controller = TestBed.inject(HttpTestingController);
    api = TestBed.inject(ApiService);
    auth = TestBed.inject(AuthService);
    admin = TestBed.inject(AdminService);
  });
  afterEach(() => controller.verify());

  it('configures production calls exclusively to the new API', () => {
    expect(production.production).toBeTrue();
    expect(production.apiBaseUrl).toBe('https://api.sarelaapp.com/api/v1');
    expect(production.apiBaseUrl).not.toContain('pgoapp.com');
  });

  it('routes login, Gmail, planning, movements and admin through the shared environment', () => {
    const checks: { run: () => void; method: string; suffix: string }[] = [
      { run: () => auth.googleStart().subscribe({ error: () => {} }), method: 'POST', suffix: '/auth/google/start' },
      { run: () => auth.refreshCurrent().subscribe({ error: () => {} }), method: 'GET', suffix: '/account' },
      { run: () => auth.logout().subscribe({ error: () => {} }), method: 'POST', suffix: '/auth/google/logout' },
      { run: () => api.gmailStatus().subscribe({ error: () => {} }), method: 'GET', suffix: '/integrations/gmail/status' },
      { run: () => api.gmailAuthorizationUrl().subscribe({ error: () => {} }), method: 'GET', suffix: '/integrations/gmail/authorization-url' },
      { run: () => api.gmailSync().subscribe({ error: () => {} }), method: 'POST', suffix: '/integrations/gmail/sync' },
      { run: () => api.gmailSyncStatus().subscribe({ error: () => {} }), method: 'GET', suffix: '/integrations/gmail/sync/status' },
      { run: () => api.gmailReview().subscribe({ error: () => {} }), method: 'GET', suffix: '/integrations/gmail/review' },
      { run: () => api.gmailDisconnect().subscribe({ error: () => {} }), method: 'DELETE', suffix: '/integrations/gmail' },
      { run: () => api.getPlanningState().subscribe({ error: () => {} }), method: 'GET', suffix: '/planning/state' },
      { run: () => api.transactions().subscribe({ error: () => {} }), method: 'GET', suffix: '/transactions' },
      { run: () => api.categories().subscribe({ error: () => {} }), method: 'GET', suffix: '/categories' },
      { run: () => admin.options().subscribe({ error: () => {} }), method: 'GET', suffix: '/admin/category-options' }
    ];
    for (const check of checks) {
      check.run();
      const request = controller.expectOne(req => req.url === environment.apiBaseUrl + check.suffix);
      expect(request.request.method).toBe(check.method);
      expect(request.request.withCredentials).toBeTrue();
      expect(request.request.headers.has('Authorization')).toBeFalse();
      request.flush({}, { status: 503, statusText: 'Test-only failure' });
    }
  });

  it('never attaches browser session cookies to another API origin', () => {
    const client = TestBed.inject(HttpClient);
    const oldUrl = 'https://api-sarela.pgoapp.com/api/v1/account';
    client.get(oldUrl).subscribe({ error: () => {} });
    let request = controller.expectOne(oldUrl);
    // On a production build this origin is different from the configured API.
    // In a development build it is also different from localhost.
    expect(request.request.withCredentials).toBeFalse();
    request.flush({}, { status: 401, statusText: 'Test-only unauthorized' });
  });
});
