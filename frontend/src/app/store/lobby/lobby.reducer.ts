import { createReducer, on } from '@ngrx/store';
import { LobbyActions } from './lobby.actions';
import { initialLobbyState, LobbyState } from './lobby.state';

export const lobbyReducer = createReducer<LobbyState>(
  initialLobbyState,

  // ── Matchmaking ────────────────────────────────────────────────────────────
  on(LobbyActions.joinQueueSuccess, (state, { entry }) => ({
    ...state,
    queueEntry: entry,
    isSearching: true,
    error: null,
  })),

  on(LobbyActions.joinQueueFailure, (state, { error }) => ({
    ...state,
    error,
    isSearching: false,
  })),

  on(LobbyActions.leaveQueueSuccess, (state) => ({
    ...state,
    queueEntry: null,
    isSearching: false,
  })),

  on(LobbyActions.matchFound, (state, { payload }) => ({
    ...state,
    matchFound: payload,
    isSearching: false,
    queueEntry: null,
    // a game against the player we invited means they accepted: we are no longer waiting
    sentInvitation: state.sentInvitation?.inviteeUsername === payload.opponentUsername ? null : state.sentInvitation,
  })),

  on(LobbyActions.clearMatchFound, (state) => ({
    ...state,
    matchFound: null,
  })),

  // ── Invitations ────────────────────────────────────────────────────────────
  on(LobbyActions.inviteReceived, (state, { invitation }) => ({
    ...state,
    pendingInvitations: [
      ...state.pendingInvitations.filter((i) => i.invitationId !== invitation.invitationId),
      invitation,
    ],
  })),

  // An invitation that is over (cancelled, declined, expired) leaves both lists: the
  // invitee's inbox, and the sender's "waiting for a response" if it was theirs
  on(LobbyActions.inviteCancelled, LobbyActions.inviteDeclined, LobbyActions.inviteExpired,
    (state, { invitationId }) => ({
      ...state,
      pendingInvitations: state.pendingInvitations.filter((i) => i.invitationId !== invitationId),
      sentInvitation: state.sentInvitation?.invitationId === invitationId ? null : state.sentInvitation,
    })
  ),

  on(LobbyActions.sendInvitationSuccess, (state, { invitation }) => ({
    ...state,
    sentInvitation: invitation,
    error: null,
  })),

  on(LobbyActions.sendInvitationFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  // While an answer is out its buttons are off; it leaves the inbox only once the server agrees,
  // and only that one: the others are still waiting
  on(LobbyActions.respondToInvitation, (state, { invitationId }) => ({
    ...state,
    respondingTo: state.respondingTo.includes(invitationId)
      ? state.respondingTo
      : [...state.respondingTo, invitationId],
  })),

  on(LobbyActions.respondToInvitationSuccess, (state, { invitationId }) => ({
    ...state,
    pendingInvitations: state.pendingInvitations.filter((i) => i.invitationId !== invitationId),
    respondingTo: state.respondingTo.filter((id) => id !== invitationId),
  })),

  on(LobbyActions.respondToInvitationFailure, (state, { invitationId, error }) => ({
    ...state,
    respondingTo: state.respondingTo.filter((id) => id !== invitationId),
    error,
  })),

  on(LobbyActions.cancelInvitationSuccess, (state) => ({
    ...state,
    sentInvitation: null,
  })),

  on(LobbyActions.loadPendingInvitationsSuccess, (state, { invitations }) => ({
    ...state,
    pendingInvitations: invitations,
  })),

  on(LobbyActions.clearSentInvitation, (state) => ({
    ...state,
    sentInvitation: null,
  })),

  on(LobbyActions.clearError, (state) => ({
    ...state,
    error: null,
  })),
);
