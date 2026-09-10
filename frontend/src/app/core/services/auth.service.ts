import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenKey = 'finoraToken';
  private readonly userKey = 'finoraUser';
  private readonly baseUrl = environment.apiBaseUrl;
  private readonly userSubject = new BehaviorSubject<AuthResponse | null>(this.readUser());
  readonly user$ = this.userSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  get token(): string {
    return localStorage.getItem(this.tokenKey) || '';
  }

  get user(): AuthResponse | null {
    return this.userSubject.value;
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/auth/login`, { email, password }).pipe(tap(user => this.store(user)));
  }

  register(name: string, email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/auth/register`, { name, email, password }).pipe(tap(user => this.store(user)));
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.userKey);
    this.userSubject.next(null);
  }

  private store(user: AuthResponse): void {
    localStorage.setItem(this.tokenKey, user.token);
    localStorage.setItem(this.userKey, JSON.stringify(user));
    this.userSubject.next(user);
  }

  private readUser(): AuthResponse | null {
    const raw = localStorage.getItem(this.userKey);
    return raw ? JSON.parse(raw) as AuthResponse : null;
  }
}
