import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: jasmine.SpyObj<AuthService> & { isLoggedIn: boolean; accessToken: string | null };

  beforeEach(() => {
    auth = Object.assign(
      jasmine.createSpyObj<AuthService>('AuthService', ['isAccessTokenExpired', 'refreshToken', 'logout']),
      { isLoggedIn: false, accessToken: null as string | null },
    );
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  describe('for a guest', () => {
    it('sends the request untouched and never tries a refresh', () => {
      auth.isAccessTokenExpired.and.returnValue(true); // no stored expiry reads as expired

      http.get('/api/games/1').subscribe();
      const req = backend.expectOne('/api/games/1');

      expect(req.request.headers.has('Authorization')).toBeFalse();
      expect(auth.refreshToken).not.toHaveBeenCalled();
      req.flush({});
    });

    it('does not log out on an error response', () => {
      http.get('/api/games/1').subscribe({ error: () => {} });
      backend.expectOne('/api/games/1').flush(null, { status: 403, statusText: 'Forbidden' });

      expect(auth.logout).not.toHaveBeenCalled();
    });
  });

  describe('with a session', () => {
    beforeEach(() => {
      auth.isLoggedIn = true;
      auth.accessToken = 'access-1';
    });

    it('attaches the access token', () => {
      auth.isAccessTokenExpired.and.returnValue(false);

      http.get('/api/users/me').subscribe();
      const req = backend.expectOne('/api/users/me');

      expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
      req.flush({});
    });

    it('refreshes an expired token first', () => {
      auth.isAccessTokenExpired.and.returnValue(true);
      auth.refreshToken.and.callFake(() => {
        auth.accessToken = 'access-2';
        return of({ accessToken: 'access-2', refreshToken: 'r', expiresIn: 900_000 });
      });

      http.get('/api/users/me').subscribe();
      const req = backend.expectOne('/api/users/me');

      expect(req.request.headers.get('Authorization')).toBe('Bearer access-2');
      req.flush({});
    });
  });
});
