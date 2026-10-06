import { combineReducers } from '@ngrx/store';
import { User } from '../../core/models';
import { makeGame } from '../../testing/game-fixtures';
import { AccountActions } from '../account/account.actions';
import { accountReducer } from '../account/account.reducer';
import { GameActions } from '../game/game.actions';
import { gameReducer } from '../game/game.reducer';
import { LobbyActions } from '../lobby/lobby.actions';
import { lobbyReducer } from '../lobby/lobby.reducer';
import { SessionActions } from './session.actions';
import { resetOnSessionEnd } from './session.meta-reducer';

describe('resetOnSessionEnd', () => {
  const reducer = resetOnSessionEnd(combineReducers({ game: gameReducer, account: accountReducer, lobby: lobbyReducer }));
  const fresh = reducer(undefined, { type: '@@init' });

  /** A state with something left over in every slice: a game, a profile, a waiting invitation. */
  function afterSomePlay() {
    let state = fresh;
    state = reducer(state, GameActions.createGameSuccess({ game: makeGame() }));
    state = reducer(state, AccountActions.loadProfileSuccess({ user: { id: 'u1', username: 'ann' } as User }));
    state = reducer(state, LobbyActions.joinQueueSuccess({ entry: { queueEntryId: 'q1' } as any }));
    return state;
  }

  it('really starts from something to wipe', () => {
    const state = afterSomePlay();

    expect(state.game.currentGame).not.toBeNull();
    expect(state.account.user).not.toBeNull();
    expect(state.lobby.isSearching).toBeTrue();
  });

  it('wipes every slice when the session ends, so the next person starts clean', () => {
    const next = reducer(afterSomePlay(), SessionActions.ended());

    expect(next).toEqual(fresh);
  });

  it('leaves the state alone for any other action', () => {
    const state = afterSomePlay();

    expect(reducer(state, GameActions.clearSelection())).toEqual(reducer(state, GameActions.clearSelection()));
    expect(reducer(state, GameActions.clearSelection()).account.user).not.toBeNull();
  });
});
