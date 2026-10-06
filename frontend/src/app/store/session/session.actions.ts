import { createActionGroup, emptyProps } from '@ngrx/store';

export const SessionActions = createActionGroup({
  source: 'Session',
  events: {
    /** The user is signed out: nothing of the session may stay in the store. */
    'Ended': emptyProps(),
  },
});
