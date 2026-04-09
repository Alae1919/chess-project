

// ─────────────────────────────────────────────────────────────────────────────
// src/app/store/account/account.reducer.ts (also contains state + selectors)
// ─────────────────────────────────────────────────────────────────────────────
import { createReducer, on, createFeatureSelector, createSelector } from '@ngrx/store';
import { User, MatchHistory, Achievement } from '../../core/models';
import { AccountActions } from './account.actions';

export interface AccountState {
  user: User | null;
  matchHistory: MatchHistory[];
  matchHistoryTotal: number;
  achievements: Achievement[];
  isLoading: boolean;
  error: string | null;
}

export const initialAccountState: AccountState = {
  user: null,
  matchHistory: [],
  matchHistoryTotal: 0,
  achievements: [],
  isLoading: false,
  error: null,
};

export const accountReducer = createReducer(
  initialAccountState,

  on(AccountActions.loadProfile, (state) => ({ ...state, isLoading: true })),

  on(AccountActions.loadProfileSuccess, (state, { user }) => ({
    ...state, user, isLoading: false,
  })),

  on(AccountActions.loadProfileFailure, (state, { error }) => ({
    ...state, isLoading: false, error,
  })),

  on(AccountActions.updatePreferencesSuccess, (state, { prefs }) => ({
    ...state,
    user: state.user ? { ...state.user, preferences: prefs } : null,
  })),

  on(AccountActions.loadMatchHistorySuccess, (state, { history, total }) => ({
    ...state, matchHistory: history, matchHistoryTotal: total,
  })),

  on(AccountActions.loadAchievementsSuccess, (state, { achievements }) => ({
    ...state, achievements,
  })),

  on(AccountActions.deleteAccountSuccess, () => initialAccountState),
);

// Selectors
const selectAccountFeature = createFeatureSelector<AccountState>('account');

export const selectUser             = createSelector(selectAccountFeature, (s) => s.user);
export const selectUserStats        = createSelector(selectUser, (u) => u?.stats ?? null);
export const selectUserPreferences  = createSelector(selectUser, (u) => u?.preferences ?? null);
export const selectMatchHistory     = createSelector(selectAccountFeature, (s) => s.matchHistory);
export const selectAchievements     = createSelector(selectAccountFeature, (s) => s.achievements);
export const selectAccountLoading   = createSelector(selectAccountFeature, (s) => s.isLoading);
