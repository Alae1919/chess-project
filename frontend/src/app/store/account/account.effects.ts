// ─────────────────────────────────────────────────────────────────────────────
// src/app/store/account/account.effects.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { AccountService } from '../../core/services/account.service';
import { AccountActions } from './account.actions';
import { AuthService } from '../../core/services/auth.service';
import { Router } from '@angular/router';


@Injectable()
export class AccountEffects {
  private actions$ = inject(Actions);
  private accountService = inject(AccountService);
  private authService = inject(AuthService);
  private router = inject(Router);

  loadProfile$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountActions.loadProfile),
      switchMap(() =>
        this.accountService.getProfile().pipe(
          map((user) => AccountActions.loadProfileSuccess({ user })),
          catchError((error) => of(AccountActions.loadProfileFailure({ error: error.message })))
        )
      )
    )
  );

  updatePreferences$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountActions.updatePreferences),
      switchMap(({ prefs }) =>
        this.accountService.updatePreferences(prefs).pipe(
          map((updated) => AccountActions.updatePreferencesSuccess({ prefs: updated })),
          catchError(() => of(AccountActions.loadProfile()))
        )
      )
    )
  );

  loadHistory$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountActions.loadMatchHistory),
      switchMap(({ page }) =>
        this.accountService.getMatchHistory(page).pipe(
          map(({ content, total }) =>
            AccountActions.loadMatchHistorySuccess({ history: content, total })
          ),
          catchError(() => of(AccountActions.loadMatchHistorySuccess({ history: [], total: 0 })))
        )
      )
    )
  );

  loadAchievements$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountActions.loadAchievements),
      switchMap(() =>
        this.accountService.getAchievements().pipe(
          map((achievements) => AccountActions.loadAchievementsSuccess({ achievements })),
          catchError(() => of(AccountActions.loadAchievementsSuccess({ achievements: [] })))
        )
      )
    )
  );

  deleteAccount$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountActions.deleteAccount),
      switchMap(() =>
        this.accountService.deleteAccount().pipe(
          map(() => AccountActions.deleteAccountSuccess()),
          catchError(() => of(AccountActions.loadProfile()))
        )
      )
    )
  );

  logoutAfterDelete$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AccountActions.deleteAccountSuccess),
        tap(() => {
          this.authService.logout();
          this.router.navigate(['/home']);
        })
      ),
    { dispatch: false }
  );
}
