import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, catchError, of, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, AccountProfile } from '../models/api.models';
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = environment.apiBaseUrl;
  private readonly userSubject = new BehaviorSubject<AuthResponse | null>(null);
  readonly user$ = this.userSubject.asObservable();
  constructor(private readonly http: HttpClient) {
    // Google tokens and opaque session cookies never enter browser storage.
    localStorage.removeItem('finoraToken');
    localStorage.removeItem('finoraUser');
  }
  get token(): string { return ''; }
  get user(): AuthResponse | null { return this.userSubject.value; }
  get isAdmin(): boolean { return this.user?.role === 'ADMIN'; }
  refreshCurrent(): Observable<AccountProfile> {
    return this.http.get<AccountProfile>(this.baseUrl + '/account').pipe(
      tap(profile => this.userSubject.next({token: '', ...profile}))
    );
  }
  restoreSession(): Observable<AccountProfile | null> {
    return this.refreshCurrent().pipe(catchError(error => error.status === 401 || error.status === 403 ? of(null) : throwError(() => error)));
  }
  googleStart(): Observable<{authorizationUrl: string}> {
    return this.http.post<{authorizationUrl: string}>(this.baseUrl + '/auth/google/start', {});
  }
  linkGoogle(password: string): Observable<{status: string}> {
    return this.http.post<{status: string}>(this.baseUrl + '/auth/google/link', {password});
  }
  logout(): Observable<void> {
    return this.http.post<void>(this.baseUrl + '/auth/google/logout', {}).pipe(tap(() => this.userSubject.next(null)));
  }
}
