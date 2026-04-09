// src/app/core/services/auth.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthTokens, LoginRequest, RegisterRequest, User } from '../models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private readonly TOKEN_KEY = 'rex_access_token';
  private readonly REFRESH_KEY = 'rex_refresh_token';
  private currentUserSubject = new BehaviorSubject<User | null>(null);

  currentUser$ = this.currentUserSubject.asObservable();

  get isLoggedIn(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }

  get accessToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  login(req: LoginRequest): Observable<AuthTokens> {
    return this.http.post<AuthTokens>(`${environment.apiUrl}/auth/login`, req).pipe(
      tap((tokens) => this.storeTokens(tokens))
    );
  }

  register(req: RegisterRequest): Observable<AuthTokens> {
    return this.http.post<AuthTokens>(`${environment.apiUrl}/auth/register`, req).pipe(
      tap((tokens) => this.storeTokens(tokens))
    );
  }

  refreshToken(): Observable<AuthTokens> {
    const refresh = localStorage.getItem(this.REFRESH_KEY);
    return this.http
      .post<AuthTokens>(`${environment.apiUrl}/auth/refresh`, { refreshToken: refresh })
      .pipe(tap((tokens) => this.storeTokens(tokens)));
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_KEY);
    this.currentUserSubject.next(null);
    this.router.navigate(['/home']);
  }

  loadCurrentUser(): Observable<User> {
    return this.http.get<User>(`${environment.apiUrl}/auth/me`).pipe(
      tap((user) => this.currentUserSubject.next(user))
    );
  }

  private storeTokens(tokens: AuthTokens): void {
    localStorage.setItem(this.TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(this.REFRESH_KEY, tokens.refreshToken);
  }
}
