import { createFeatureSelector, createSelector } from '@ngrx/store';
import { LobbyState } from './lobby.state';

export const selectLobbyState = createFeatureSelector<LobbyState>('lobby');

export const selectQueueEntry       = createSelector(selectLobbyState, (s) => s.queueEntry);
export const selectIsSearching      = createSelector(selectLobbyState, (s) => s.isSearching);
export const selectMatchFound       = createSelector(selectLobbyState, (s) => s.matchFound);
export const selectPendingInvitations = createSelector(selectLobbyState, (s) => s.pendingInvitations);
export const selectSentInvitation   = createSelector(selectLobbyState, (s) => s.sentInvitation);
export const selectLobbyError       = createSelector(selectLobbyState, (s) => s.error);
export const selectRespondingTo     = createSelector(selectLobbyState, (s) => s.respondingTo);
