import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom, take, toArray } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let backend: HttpTestingController;
  let navigate: jasmine.Spy;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    auth = TestBed.inject(AuthService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  function signIn() {
    auth.login({ email: 'a@b.c', password: 'secret123' }).subscribe();
    backend.expectOne(`${environment.apiUrl}/auth/login`)
      .flush({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900_000 });
  }

  describe('logout', () => {
    it('tells the server to end the session, so the refresh token stops working everywhere', () => {
      signIn();

      auth.logout();

      const req = backend.expectOne(`${environment.apiUrl}/auth/logout`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
      req.flush(null);
    });

    it('signs out here at once, without waiting for the server', () => {
      signIn();

      auth.logout();

      expect(auth.isLoggedIn).toBeFalse();
      expect(localStorage.getItem('rex_refresh_token')).toBeNull();
      expect(navigate).toHaveBeenCalledWith(['/home']);
      backend.expectOne(`${environment.apiUrl}/auth/logout`).flush(null);
    });

    it('still signs out when the server can\'t be reached', () => {
      signIn();

      auth.logout();
      backend.expectOne(`${environment.apiUrl}/auth/logout`).error(new ProgressEvent('error'));

      expect(auth.isLoggedIn).toBeFalse();
    });

    it('sends nothing when there is no session to end', () => {
      auth.logout();

      backend.expectNone(`${environment.apiUrl}/auth/logout`);
    });

    it('announces that the session is over', async () => {
      signIn();
      const states = firstValueFrom(auth.loggedIn$.pipe(take(2), toArray()));

      auth.logout();
      backend.expectOne(`${environment.apiUrl}/auth/logout`).flush(null);

      expect(await states).toEqual([true, false]);
    });
  });
});
