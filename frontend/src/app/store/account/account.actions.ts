// src/app/store/account/account.actions.ts
import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { User, UserPreferences, MatchHistory, Achievement } from '../../core/models';

export const AccountActions = createActionGroup({
  source: 'Account',
  events: {
    'Load Profile': emptyProps(),
    'Load Profile Success': props<{ user: User }>(),
    'Load Profile Failure': props<{ error: string }>(),

    'Update Preferences': props<{ prefs: Partial<UserPreferences> }>(),
    'Update Preferences Success': props<{ prefs: UserPreferences }>(),

    'Load Match History': props<{ page: number }>(),
    'Load Match History Success': props<{ history: MatchHistory[]; total: number }>(),

    'Load Achievements': emptyProps(),
    'Load Achievements Success': props<{ achievements: Achievement[] }>(),

    'Change Password': props<{ oldPassword: string; newPassword: string }>(),
    'Change Password Success': emptyProps(),
    'Change Password Failure': props<{ error: string }>(),

    'Delete Account': emptyProps(),
    'Delete Account Success': emptyProps(),
  },
});
