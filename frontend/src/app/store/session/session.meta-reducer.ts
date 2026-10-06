import { ActionReducer } from '@ngrx/store';
import { SessionActions } from './session.actions';

/**
 * Wipes every slice of the store when the session ends. Each slice holds something that
 * belongs to the person who was signed in (the profile, the game on screen, queued invitations),
 * and resetting them one at a time leaves whichever one is forgotten for the next person.
 */
export function resetOnSessionEnd<S>(reducer: ActionReducer<S>): ActionReducer<S> {
  return (state, action) =>
    reducer(action.type === SessionActions.ended.type ? undefined : state, action);
}
