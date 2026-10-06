import { GameInvitation, MatchFoundPayload } from '../../core/models';
import { LobbyActions } from './lobby.actions';
import { lobbyReducer } from './lobby.reducer';
import { initialLobbyState, LobbyState } from './lobby.state';

function invitation(id: string): GameInvitation {
  return {
    invitationId: id, inviterUsername: 'ann', inviteeUsername: 'bob', status: 'pending',
    timeControlType: 'blitz', timeControlInitialMs: 300_000, timeControlIncrementMs: 0,
    createdAt: '2026-01-01T00:00:00Z', expiresAt: '2026-01-01T00:02:00Z',
  };
}

function match(opponentUsername: string): MatchFoundPayload {
  return {
    gameId: 'game-1', opponentUsername, opponentElo: 1200, playerColor: 'white',
    timeControlType: 'blitz', timeControlInitialMs: 300_000, timeControlIncrementMs: 0,
  };
}

describe('lobbyReducer invitations', () => {
  const waiting: LobbyState = {
    ...initialLobbyState,
    sentInvitation: invitation('sent-1'),
    pendingInvitations: [invitation('inbox-1')],
  };

  it('clears the sender\'s waiting state when the invitation is declined', () => {
    const next = lobbyReducer(waiting, LobbyActions.inviteDeclined({ invitationId: 'sent-1' }));

    expect(next.sentInvitation).toBeNull();
    expect(next.pendingInvitations.length).toBe(1);
  });

  it('clears it when the invitation expires', () => {
    expect(lobbyReducer(waiting, LobbyActions.inviteExpired({ invitationId: 'sent-1' })).sentInvitation).toBeNull();
  });

  it('removes an expired or cancelled invitation from the inbox', () => {
    expect(lobbyReducer(waiting, LobbyActions.inviteExpired({ invitationId: 'inbox-1' })).pendingInvitations).toEqual([]);
    expect(lobbyReducer(waiting, LobbyActions.inviteCancelled({ invitationId: 'inbox-1' })).pendingInvitations).toEqual([]);
  });

  it('clears it when the invited player accepts and the game starts', () => {
    const next = lobbyReducer(waiting, LobbyActions.matchFound({ payload: match('bob') }));

    expect(next.sentInvitation).toBeNull();
  });

  it('keeps it when a match against someone else is found', () => {
    const next = lobbyReducer(waiting, LobbyActions.matchFound({ payload: match('carol') }));

    expect(next.sentInvitation?.invitationId).toBe('sent-1');
  });

  it('leaves the waiting state alone when another invitation ends', () => {
    const next = lobbyReducer(waiting, LobbyActions.inviteExpired({ invitationId: 'someone-else' }));

    expect(next.sentInvitation?.invitationId).toBe('sent-1');
    expect(next.pendingInvitations.length).toBe(1);
  });
});

describe('lobbyReducer answering invitations', () => {
  const inbox: LobbyState = {
    ...initialLobbyState,
    pendingInvitations: [invitation('a'), invitation('b')],
  };

  it('marks an invitation as being answered while the request is out', () => {
    const next = lobbyReducer(inbox, LobbyActions.respondToInvitation({ invitationId: 'a', response: 'accept' }));

    expect(next.respondingTo).toEqual(['a']);
    expect(next.pendingInvitations.length).toBe(2);   // still there: the answer may fail
  });

  it('removes only the invitation that was answered', () => {
    const asking = lobbyReducer(inbox, LobbyActions.respondToInvitation({ invitationId: 'a', response: 'decline' }));

    const next = lobbyReducer(asking, LobbyActions.respondToInvitationSuccess({ invitationId: 'a' }));

    expect(next.pendingInvitations.map((i) => i.invitationId)).toEqual(['b']);
    expect(next.respondingTo).toEqual([]);
  });

  it('keeps the invitation and says why when the answer fails', () => {
    const asking = lobbyReducer(inbox, LobbyActions.respondToInvitation({ invitationId: 'a', response: 'accept' }));

    const next = lobbyReducer(asking, LobbyActions.respondToInvitationFailure({ invitationId: 'a', error: 'Invitation has expired' }));

    expect(next.pendingInvitations.length).toBe(2);
    expect(next.respondingTo).toEqual([]);
    expect(next.error).toBe('Invitation has expired');
  });

  it('answers several invitations independently', () => {
    let state = lobbyReducer(inbox, LobbyActions.respondToInvitation({ invitationId: 'a', response: 'accept' }));
    state = lobbyReducer(state, LobbyActions.respondToInvitation({ invitationId: 'b', response: 'decline' }));
    expect(state.respondingTo).toEqual(['a', 'b']);

    state = lobbyReducer(state, LobbyActions.respondToInvitationSuccess({ invitationId: 'b' }));

    expect(state.respondingTo).toEqual(['a']);
  });
});

