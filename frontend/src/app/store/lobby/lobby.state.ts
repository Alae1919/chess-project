import { GameInvitation, MatchFoundPayload, QueueEntry } from '../../core/models';

export interface LobbyState {
  queueEntry: QueueEntry | null;
  pendingInvitations: GameInvitation[];
  sentInvitation: GameInvitation | null;
  isSearching: boolean;
  matchFound: MatchFoundPayload | null;
  error: string | null;
}

export const initialLobbyState: LobbyState = {
  queueEntry: null,
  pendingInvitations: [],
  sentInvitation: null,
  isSearching: false,
  matchFound: null,
  error: null,
};
