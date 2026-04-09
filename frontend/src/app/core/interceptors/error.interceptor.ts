// ─────────────────────────────────────────────────────────────────────────────
// src/app/core/interceptors/error.interceptor.ts
// ─────────────────────────────────────────────────────────────────────────────
import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Attempt token refresh then retry once
        return auth.refreshToken().pipe(
          switchMap(() => next(req.clone({ setHeaders: { Authorization: `Bearer ${auth.accessToken}` } }))),
          catchError((refreshErr) => {
            auth.logout();
            return throwError(() => refreshErr);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
