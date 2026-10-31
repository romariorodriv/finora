import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { environment } from '../../../environments/environment';
import { testPlan } from '../../premium.test-fixtures';

describe('Planning HTTP contract and JWT preservation', () => {
  let api: ApiService, http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting()]});
    api=TestBed.inject(ApiService);http=TestBed.inject(HttpTestingController);
    localStorage.setItem('finoraToken','unit-test-token');
  });
  afterEach(() => { http.verify();localStorage.removeItem('finoraToken'); });
  it('reads saved state with the JWT and unchanged endpoint', () => {
    api.getPlanningState().subscribe(state=>expect(state).toEqual(testPlan));
    const request=http.expectOne(`${environment.apiBaseUrl}/planning/state`);
    expect(request.request.method).toBe('GET');expect(request.request.headers.get('Authorization')).toBe('Bearer unit-test-token');request.flush(testPlan);
  });
  it('saves only the profile contract and creates/deletes payments with existing endpoints', () => {
    api.savePlanningProfile(testPlan.profile!).subscribe();
    const profile=http.expectOne(`${environment.apiBaseUrl}/planning/state`);expect(profile.request.method).toBe('PUT');expect(profile.request.body).toEqual(testPlan.profile);profile.flush(testPlan);
    const payment={name:'Pago QA',dueDate:'2098-12-31',amount:300};api.addPlanningPayment(payment).subscribe();
    const create=http.expectOne(`${environment.apiBaseUrl}/planning/payments`);expect(create.request.method).toBe('POST');expect(create.request.body).toEqual(payment);create.flush(testPlan);
    api.deletePlanningPayment(9).subscribe();const remove=http.expectOne(`${environment.apiBaseUrl}/planning/payments/9`);expect(remove.request.method).toBe('DELETE');remove.flush({...testPlan,payments:[]});
  });
});
