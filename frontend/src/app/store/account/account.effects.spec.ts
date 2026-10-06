import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { Observable, Subject, of, throwError } from 'rxjs';
import { AccountService } from '../../core/services/account.service';
import { AuthService } from '../../core/services/auth.service';
import { AccountActions } from './account.actions';
import { AccountEffects } from './account.effects';
import { accountReducer } from './account.reducer';
import { initialAccountState } from './account.reducer';

describe('AccountEffects — deleting the account', () => {
  let actions$: Subject<Action>;
  let effects: AccountEffects;
  let accountService: jasmine.SpyObj<AccountService>;
  let auth: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    actions$ = new Subject<Action>();
    accountService = jasmine.createSpyObj<AccountService>('AccountService',
      ['deleteAccount', 'getProfile', 'getMatchHistory', 'getAchievements', 'updatePreferences', 'updateProfile']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['logout']);
    TestBed.configureTestingModule({
      providers: [
        AccountEffects,
        provideMockActions(() => actions$),
        provideRouter([]),
        { provide: AccountService, useValue: accountService },
        { provide: AuthService, useValue: auth },
      ],
    });
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    effects = TestBed.inject(AccountEffects);
  });

  function collect(effect$: Observable<Action>): Action[] {
    const out: Action[] = [];
    effect$.subscribe((a) => out.push(a));
    return out;
  }

  it('sends the password with the request', () => {
    accountService.deleteAccount.and.returnValue(of(undefined));
    collect(effects.deleteAccount$);

    actions$.next(AccountActions.deleteAccount({ password: 'secret123' }));

    expect(accountService.deleteAccount).toHaveBeenCalledWith('secret123');
  });

  it('is deleted when the server agrees', () => {
    accountService.deleteAccount.and.returnValue(of(undefined));
    const out = collect(effects.deleteAccount$);

    actions$.next(AccountActions.deleteAccount({ password: 'secret123' }));

    expect(out).toEqual([AccountActions.deleteAccountSuccess()]);
  });

  it('says why when the password is wrong, and keeps the account', () => {
    accountService.deleteAccount.and.returnValue(throwError(() => ({ error: { detail: 'Incorrect password' } })));
    const out = collect(effects.deleteAccount$);

    actions$.next(AccountActions.deleteAccount({ password: 'wrong' }));

    expect(out).toEqual([AccountActions.deleteAccountFailure({ error: 'Incorrect password' })]);
  });

  it('shows that error in the account state, and clears it for the next attempt', () => {
    const failed = accountReducer(initialAccountState, AccountActions.deleteAccountFailure({ error: 'Incorrect password' }));
    expect(failed.error).toBe('Incorrect password');

    const retry = accountReducer(failed, AccountActions.deleteAccount({ password: 'secret123' }));
    expect(retry.error).toBeNull();
  });

  it('signs out after the account is gone', () => {
    collect(effects.logoutAfterDelete$);

    actions$.next(AccountActions.deleteAccountSuccess());

    expect(auth.logout).toHaveBeenCalled();
  });
});
