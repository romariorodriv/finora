import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { environment } from '../../../environments/environment';
import { testPlan } from '../../premium.test-fixtures';

describe('Planning HTTP contract and secure session preservation', () => {
  let api: ApiService, http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting()]});
    api=TestBed.inject(ApiService);http=TestBed.inject(HttpTestingController);
    localStorage.setItem('finoraToken','unit-test-token');
  });
  afterEach(() => { http.verify();localStorage.removeItem('finoraToken'); });
  it('reads saved state with credentials and the unchanged endpoint', () => {
    api.getPlanningState().subscribe(state=>expect(state).toEqual(testPlan));
    const request=http.expectOne(`${environment.apiBaseUrl}/planning/state`);
    expect(request.request.method).toBe('GET');expect(request.request.withCredentials).toBeTrue();expect(request.request.headers.has('Authorization')).toBeFalse();request.flush(testPlan);
  });
  it('saves only the profile contract and creates/deletes payments with existing endpoints', () => {
    api.savePlanningProfile(testPlan.profile!).subscribe();
    const profile=http.expectOne(`${environment.apiBaseUrl}/planning/state`);expect(profile.request.method).toBe('PUT');expect(profile.request.body).toEqual(testPlan.profile);profile.flush(testPlan);
    const payment={name:'Pago QA',dueDate:'2098-12-31',amount:300};api.addPlanningPayment(payment).subscribe();
    const create=http.expectOne(`${environment.apiBaseUrl}/planning/payments`);expect(create.request.method).toBe('POST');expect(create.request.body).toEqual(payment);create.flush(testPlan);
    api.deletePlanningPayment(9).subscribe();const remove=http.expectOne(`${environment.apiBaseUrl}/planning/payments/9`);expect(remove.request.method).toBe('DELETE');remove.flush({...testPlan,payments:[]});
  });  it('sends explicit date, search, category, type, currency and server page parameters for history', () => {
    const filters={from:'2026-08-01',to:'2026-10-31',search:'Cafe',category:'Alimentacion',type:'EXPENSE',currency:'USD'};
    api.transactionHistory(filters,2,12).subscribe(page=>expect(page.total).toBe(25));
    const request=http.expectOne(r=>r.url.endsWith('/transactions/history'));
    for(const [key,value] of Object.entries(filters))expect(request.request.params.get(key)).toBe(value);
    expect(request.request.params.get('page')).toBe('2');expect(request.request.params.get('size')).toBe('12');
    request.flush({items:[],total:25,page:2,totalPages:3});
  });
  it('sends the resulting balance together with payment completion and passes assistant currency', () => {
    api.completePlanningPayment(7,700).subscribe();
    const paid=http.expectOne(`${environment.apiBaseUrl}/planning/payments/7/complete`);
    expect(paid.request.body).toEqual({resultingBalance:700});paid.flush(testPlan);
    api.askAssistant('Cuanto llevo este mes?','USD').subscribe();
    const assistant=http.expectOne(`${environment.apiBaseUrl}/assistant`);
    expect(assistant.request.body.currency).toBe('USD');assistant.flush({answer:'USD 20.00',suggestions:[],mode:'LOCAL_ANALYTICS'});
  });

});
