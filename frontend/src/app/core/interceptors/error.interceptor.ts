// ─────────────────────────────────────────────────────────────────────────────
// src/app/core/interceptors/error.interceptor.ts
// ─────────────────────────────────────────────────────────────────────────────
import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // 401s are fully handled by authInterceptor. Handle all other errors here.
      if (error.status === 0) {
        console.error('[Network] Request failed — no server response', req.url);
      } else if (error.status >= 500) {
        console.error(`[Server Error ${error.status}]`, error.message);
      } else if (error.status === 403) {
        console.warn('[Forbidden] Insufficient permissions', req.url);
      } else if (error.status === 404) {
        console.warn('[Not Found]', req.url);
      }
      return throwError(() => error);
    })
  );
};
