// src/app/core/services/auth.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, distinctUntilChanged, finalize, shareReplay, tap } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthTokens, LoginRequest, RegisterRequest, User } from '../models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private readonly TOKEN_KEY   = 'rex_access_token';
  private readonly REFRESH_KEY = 'rex_refresh_token';
  private readonly EXPIRES_KEY = 'rex_token_expires_at';

  private currentUserSubject = new BehaviorSubject<User | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();

  /** Whether a session exists; emits on login, register and logout (not on refresh) */
  private loggedInSubject = new BehaviorSubject<boolean>(!!localStorage.getItem(this.TOKEN_KEY));
  readonly loggedIn$ = this.loggedInSubject.pipe(distinctUntilChanged());

  // Sentinel for the in-flight refresh. Null means no refresh is running.
  private refresh$: Observable<AuthTokens> | null = null;

  get isLoggedIn(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }

  get accessToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  isAccessTokenExpired(): boolean {
    const expiresAt = localStorage.getItem(this.EXPIRES_KEY);
    if (!expiresAt) return true;
    // 30-second buffer: refresh slightly before hard expiry.
    return Date.now() > Number(expiresAt) - 30_000;
  }

  login(req: LoginRequest): Observable<AuthTokens> {
    return this.http
      .post<AuthTokens>(`${environment.apiUrl}/auth/login`, req)
      .pipe(tap((tokens) => this.storeTokens(tokens)));
  }

  register(req: RegisterRequest): Observable<AuthTokens> {
    return this.http
      .post<AuthTokens>(`${environment.apiUrl}/auth/register`, req)
      .pipe(tap((tokens) => this.storeTokens(tokens)));
  }

  refreshToken(): Observable<AuthTokens> {
    // Return the in-flight observable so all concurrent callers share one HTTP request.
    if (this.refresh$) return this.refresh$;

    const refresh = localStorage.getItem(this.REFRESH_KEY);
    this.refresh$ = this.http
      .post<AuthTokens>(`${environment.apiUrl}/auth/refresh`, { refreshToken: refresh })
      .pipe(
        tap((tokens) => this.storeTokens(tokens)),
        shareReplay(1),                        // multicast result to all waiting subscribers
        finalize(() => { this.refresh$ = null; }) // clear sentinel on complete or error
      );

    return this.refresh$;
  }

  /**
   * Ends the session. The refresh token is revoked on the server too, so a copy of it (on
   * another device, or taken from this one) stops working. Signing out here doesn't wait for
   * that: the server being unreachable must not keep someone signed in.
   */
  logout(): void {
    const refresh = localStorage.getItem(this.REFRESH_KEY);
    if (refresh) {
      this.http.post(`${environment.apiUrl}/auth/logout`, { refreshToken: refresh })
        .subscribe({ error: () => { /* signed out here regardless */ } });
    }
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_KEY);
    localStorage.removeItem(this.EXPIRES_KEY);
    this.currentUserSubject.next(null);
    this.loggedInSubject.next(false);
    this.router.navigate(['/home']);
  }

  loadCurrentUser(): Observable<User> {
    return this.http
      .get<User>(`${environment.apiUrl}/users/me`)
      .pipe(tap((user) => this.currentUserSubject.next(user)));
  }

  private storeTokens(tokens: AuthTokens): void {
    localStorage.setItem(this.TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(this.REFRESH_KEY, tokens.refreshToken);
    localStorage.setItem(this.EXPIRES_KEY, String(Date.now() + tokens.expiresIn));
    this.loggedInSubject.next(true);
  }
}
