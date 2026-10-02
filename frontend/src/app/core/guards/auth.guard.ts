// src/app/core/guards/auth.guard.ts
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isLoggedIn) return true;
  // Log in, then come back to the page that was asked for (e.g. a game link)
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
