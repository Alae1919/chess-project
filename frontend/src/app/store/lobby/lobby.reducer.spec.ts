import { GameInvitation } from '../../core/models';
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

  it('leaves the waiting state alone when another invitation ends', () => {
    const next = lobbyReducer(waiting, LobbyActions.inviteExpired({ invitationId: 'someone-else' }));

    expect(next.sentInvitation?.invitationId).toBe('sent-1');
    expect(next.pendingInvitations.length).toBe(1);
  });
});
