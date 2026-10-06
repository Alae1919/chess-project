import { GameInvitation, MatchFoundPayload, QueueEntry } from '../../core/models';

export interface LobbyState {
  queueEntry: QueueEntry | null;
  pendingInvitations: GameInvitation[];
  sentInvitation: GameInvitation | null;
  isSearching: boolean;
  matchFound: MatchFoundPayload | null;
  /** Invitations whose answer (accept or decline) is on its way to the server */
  respondingTo: string[];
  error: string | null;
}

export const initialLobbyState: LobbyState = {
  queueEntry: null,
  pendingInvitations: [],
  sentInvitation: null,
  isSearching: false,
  matchFound: null,
  respondingTo: [],
  error: null,
};
