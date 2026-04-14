import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { GameInvitation, MatchFoundPayload, QueueEntry } from '../../core/models';
import { JoinQueueRequest } from '../../core/services/matchmaking.service';
import { SendInvitationRequest } from '../../core/services/invitation.service';

export const LobbyActions = createActionGroup({
  source: 'Lobby',
  events: {
    // ── Matchmaking ──────────────────────────────────────────────────────────
    'Join Queue': props<{ req: JoinQueueRequest }>(),
    'Join Queue Success': props<{ entry: QueueEntry }>(),
    'Join Queue Failure': props<{ error: string }>(),

    'Leave Queue': emptyProps(),
    'Leave Queue Success': emptyProps(),

    // ── WebSocket events ─────────────────────────────────────────────────────
    'Match Found': props<{ payload: MatchFoundPayload }>(),

    'Invite Received': props<{ invitation: GameInvitation }>(),
    'Invite Declined': props<{ invitationId: string }>(),
    'Invite Cancelled': props<{ invitationId: string }>(),

    // ── Friend invitations ───────────────────────────────────────────────────
    'Send Invitation': props<{ req: SendInvitationRequest }>(),
    'Send Invitation Success': props<{ invitation: GameInvitation }>(),
    'Send Invitation Failure': props<{ error: string }>(),

    'Respond To Invitation': props<{ invitationId: string; response: 'accept' | 'decline' }>(),
    'Respond To Invitation Success': props<{ gameId?: string }>(),
    'Respond To Invitation Failure': props<{ error: string }>(),

    'Cancel Invitation': props<{ invitationId: string }>(),
    'Cancel Invitation Success': emptyProps(),

    'Load Pending Invitations': emptyProps(),
    'Load Pending Invitations Success': props<{ invitations: GameInvitation[] }>(),

    // ── Misc ─────────────────────────────────────────────────────────────────
    'Clear Match Found': emptyProps(),
    'Clear Error': emptyProps(),
    'Clear Sent Invitation': emptyProps(),
  },
});
