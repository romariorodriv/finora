import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { environment } from '../../../environments/environment';

describe('Sarela movements and assistant HTTP contracts', () => {
  let api: ApiService, http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()]});
    api=TestBed.inject(ApiService);
    http=TestBed.inject(HttpTestingController);
    localStorage.setItem('finoraToken','unit-test-token');
  });
  afterEach(() => {http.verify();localStorage.removeItem('finoraToken');});

  it('sends date, search, category, type, currency and server pagination for transaction history', () => {
    const filters={from:'2026-08-01',to:'2026-10-31',search:'Cafe',category:'Alimentacion',type:'EXPENSE',currency:'USD'};
    api.transactionHistory(filters,2,12).subscribe(page=>expect(page.total).toBe(25));
    const request=http.expectOne(r=>r.url.endsWith('/transactions/history'));
    for(const [key,value] of Object.entries(filters))expect(request.request.params.get(key)).toBe(value);
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('12');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({items:[],total:25,page:2,totalPages:3});
  });

  it('preserves assistant selected currency', () => {
    api.askAssistant('Cuanto llevo este mes?','USD').subscribe();
    const request=http.expectOne(`${environment.apiBaseUrl}/assistant`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.currency).toBe('USD');
    request.flush({answer:'USD 20.00',suggestions:[],mode:'LOCAL_ANALYTICS'});
  });
});
