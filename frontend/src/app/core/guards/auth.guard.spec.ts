import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  function runGuard(isLoggedIn: boolean, url: string) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { isLoggedIn } }],
    });
    return TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot));
  }

  it('lets a logged-in user through', () => {
    expect(runGuard(true, '/account')).toBeTrue();
  });

  it('sends a guest to the login page, remembering where they were going', () => {
    const result = runGuard(false, '/game/abc');

    const router = TestBed.inject(Router);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login?returnUrl=%2Fgame%2Fabc');
  });
});
