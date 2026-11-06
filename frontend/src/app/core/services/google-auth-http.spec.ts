import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { ApiService } from './api.service';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { environment } from '../../../environments/environment';

describe('Google sign-in and cookie session HTTP contract', () => {
 let auth: AuthService, http: HttpTestingController, client: HttpClient;
 beforeEach(() => {
  localStorage.setItem('finoraToken','legacy-token-to-clear');
  TestBed.configureTestingModule({providers:[provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting()]});
  auth=TestBed.inject(AuthService);http=TestBed.inject(HttpTestingController);client=TestBed.inject(HttpClient);
 });
 afterEach(()=>http.verify());
 it('removes browser tokens and starts Google with a credentialed POST',()=>{
  expect(localStorage.getItem('finoraToken')).toBeNull();
  auth.googleStart().subscribe();
  const request=http.expectOne(environment.apiBaseUrl+'/auth/google/start');
  expect(request.request.method).toBe('POST');expect(request.request.withCredentials).toBeTrue();
  expect(request.request.headers.has('Authorization')).toBeFalse();
  request.flush({authorizationUrl:'https://accounts.google.com/o/oauth2/v2/auth?state=test'});
 });
 it('restores only server account metadata and never stores a bearer token',()=>{
  auth.restoreSession().subscribe();
  const request=http.expectOne(environment.apiBaseUrl+'/account');
  request.flush({userId:5,name:'QA',email:'qa@example.test',role:'ADMIN'});
  expect(auth.isAdmin).toBeTrue();expect(auth.token).toBe('');
  expect(localStorage.getItem('finoraUser')).toBeNull();
 });
 it('distinguishes absent session from an unavailable backend',()=>{
  let value:unknown='pending';auth.restoreSession().subscribe(result=>value=result);
  http.expectOne(environment.apiBaseUrl+'/account').flush({}, {status:401,statusText:'Unauthorized'});
  expect(value).toBeNull();
  let failed=false;auth.restoreSession().subscribe({error:()=>failed=true});
  http.expectOne(environment.apiBaseUrl+'/account').flush({}, {status:503,statusText:'Unavailable'});
  expect(failed).toBeTrue();
 });
 it('does not attach sessions or stored credentials to unrelated origins',()=>{
  localStorage.setItem('finoraToken','do-not-forward');
  client.get('https://unrelated.example.test/data').subscribe();
  const request=http.expectOne('https://unrelated.example.test/data');
  expect(request.request.withCredentials).toBeFalse();expect(request.request.headers.has('Authorization')).toBeFalse();request.flush({});
  localStorage.removeItem('finoraToken');
 });
 it('links with explicit proof and leaves role assignment to the server',()=>{
  auth.linkGoogle('proof-only').subscribe();
  const request=http.expectOne(environment.apiBaseUrl+'/auth/google/link');
  expect(request.request.body).toEqual({password:'proof-only'});expect(request.request.withCredentials).toBeTrue();request.flush({status:'LINKED'});
 });
 it('keeps the visible session on failed logout and clears it after server confirmation',()=>{
  auth.refreshCurrent().subscribe();http.expectOne(environment.apiBaseUrl+'/account').flush({userId:1,name:'QA',email:'qa@example.test',role:'USER'});
  auth.logout().subscribe({error:()=>{}});
  http.expectOne(environment.apiBaseUrl+'/auth/google/logout').flush({}, {status:503,statusText:'Unavailable'});
  expect(auth.user).not.toBeNull();
  auth.logout().subscribe();http.expectOne(environment.apiBaseUrl+'/auth/google/logout').flush(null);
  expect(auth.user).toBeNull();
 });
 it('requires explicit typed deletion proof and first-party session cookies',()=>{
  const api=TestBed.inject(ApiService);
  api.requestDataDeletion().subscribe();
  const request=http.expectOne(environment.apiBaseUrl+'/account/deletion-request');
  expect(request.request.method).toBe('POST');
  expect(request.request.withCredentials).toBeTrue();
  expect(request.request.body).toEqual({confirmed:true,confirmationText:'ELIMINAR'});
  expect(request.request.headers.has('Authorization')).toBeFalse();
  request.flush({status:'DELETED',message:'Cuenta eliminada'});
 });
 it('queues manual import and reads progress independently without accepting a user ID',()=>{
  const api=TestBed.inject(ApiService);api.gmailSync().subscribe();
  const enqueue=http.expectOne(environment.apiBaseUrl+'/integrations/gmail/sync');
  expect(enqueue.request.method).toBe('POST');expect(enqueue.request.body).toEqual({});
  enqueue.flush({status:'PENDING'});
  api.gmailSyncStatus().subscribe();
  const progress=http.expectOne(environment.apiBaseUrl+'/integrations/gmail/sync/status');
  expect(progress.request.method).toBe('GET');expect(progress.request.withCredentials).toBeTrue();progress.flush({status:'PENDING'});
 });
});
