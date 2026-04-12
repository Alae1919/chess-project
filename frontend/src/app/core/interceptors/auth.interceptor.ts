// ─────────────────────────────────────────────────────────────────────────────
// src/app/core/interceptors/auth.interceptor.ts
// ─────────────────────────────────────────────────────────────────────────────
import { HttpInterceptorFn, HttpRequest, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

function addAuthHeader(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

function isAuthEndpoint(url: string): boolean {
  return url.includes('/auth/');
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  // ── Auth endpoints bypass all token logic to prevent refresh loops ────────
  if (isAuthEndpoint(req.url)) {
    return next(req);
  }

  // ── Proactive refresh: token is expired or missing ────────────────────────
  if (auth.isAccessTokenExpired()) {
    return auth.refreshToken().pipe(
      switchMap(() => {
        const token = auth.accessToken;
        if (!token) { auth.logout(); return throwError(() => new Error('No token after refresh')); }
        return next(addAuthHeader(req, token)).pipe(
          catchError((err: HttpErrorResponse) => {
            if (err.status === 401) auth.logout();
            return throwError(() => err);
          })
        );
      }),
      catchError((err) => { auth.logout(); return throwError(() => err); })
    );
  }

  // ── Normal path: attach token, handle reactive 401 ───────────────────────
  const token = auth.accessToken;
  return next(token ? addAuthHeader(req, token) : req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401) return throwError(() => error);

      return auth.refreshToken().pipe(
        switchMap(() => {
          const newToken = auth.accessToken;
          if (!newToken) { auth.logout(); return throwError(() => new Error('No token after refresh')); }
          return next(addAuthHeader(req, newToken));
        }),
        catchError((refreshErr) => { auth.logout(); return throwError(() => refreshErr); })
      );
    })
  );
};