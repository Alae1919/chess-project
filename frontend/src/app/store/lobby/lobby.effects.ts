import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { catchError, filter, map, mergeMap, switchMap, tap } from 'rxjs/operators';

import { LobbyActions } from './lobby.actions';
import { MatchmakingService } from '../../core/services/matchmaking.service';
import { InvitationService } from '../../core/services/invitation.service';
import { LobbyWebSocketService } from '../../core/services/lobby-websocket.service';
import { GameInvitation, MatchFoundPayload } from '../../core/models';
import { Game } from '../../core/models';
import { errorMessage } from '../../core/utils/error-message';

@Injectable()
export class LobbyEffects {
  private actions$ = inject(Actions);
  private matchmakingService = inject(MatchmakingService);
  private invitationService = inject(InvitationService);
  private lobbyWsService = inject(LobbyWebSocketService);
  private router = inject(Router);

  // ── Matchmaking HTTP effects ───────────────────────────────────────────────

  joinQueue$ = createEffect(() =>
    this.actions$.pipe(
      ofType(LobbyActions.joinQueue),
      switchMap(({ req }) =>
        this.matchmakingService.joinQueue(req).pipe(
          map((entry) => LobbyActions.joinQueueSuccess({ entry })),
          catchError((err) => of(LobbyActions.joinQueueFailure({ error: errorMessage(err) })))
        )
      )
    )
  );

  leaveQueue$ = createEffect(() =>
    this.actions$.pipe(
      ofType(LobbyActions.leaveQueue),
      switchMap(() =>
        this.matchmakingService.leaveQueue().pipe(
          map(() => LobbyActions.leaveQueueSuccess()),
          catchError(() => of(LobbyActions.leaveQueueSuccess()))
        )
      )
    )
  );

  // ── Invitation HTTP effects ────────────────────────────────────────────────

  sendInvitation$ = createEffect(() =>
    this.actions$.pipe(
      ofType(LobbyActions.sendInvitation),
      switchMap(({ req }) =>
        this.invitationService.sendInvitation(req).pipe(
          map((invitation) => LobbyActions.sendInvitationSuccess({ invitation })),
          catchError((err) => of(LobbyActions.sendInvitationFailure({ error: errorMessage(err) })))
        )
      )
    )
  );

  respondToInvitation$ = createEffect(() =>
    this.actions$.pipe(
      ofType(LobbyActions.respondToInvitation),
      // mergeMap: answers to different invitations are independent, and switchMap would drop the first
      mergeMap(({ invitationId, response }) =>
        this.invitationService.respondToInvitation(invitationId, response).pipe(
          map((result) => {
            const gameId = response === 'accept' ? (result as Game).id : undefined;
            return LobbyActions.respondToInvitationSuccess({ invitationId, gameId });
          }),
          catchError((err) => of(LobbyActions.respondToInvitationFailure({ invitationId, error: errorMessage(err) })))
        )
      )
    )
  );

  cancelInvitation$ = createEffect(() =>
    this.actions$.pipe(
      ofType(LobbyActions.cancelInvitation),
      switchMap(({ invitationId }) =>
        this.invitationService.cancelInvitation(invitationId).pipe(
          map(() => LobbyActions.cancelInvitationSuccess()),
          catchError(() => of(LobbyActions.cancelInvitationSuccess()))
        )
      )
    )
  );

  loadPendingInvitations$ = createEffect(() =>
    this.actions$.pipe(
      ofType(LobbyActions.loadPendingInvitations),
      switchMap(() =>
        this.invitationService.getPendingInvitations().pipe(
          map((invitations) => LobbyActions.loadPendingInvitationsSuccess({ invitations })),
          catchError(() => of(LobbyActions.loadPendingInvitationsSuccess({ invitations: [] })))
        )
      )
    )
  );

  // ── Lobby WebSocket bridge ─────────────────────────────────────────────────

  lobbyWsBridge$ = createEffect(() =>
    this.lobbyWsService.messages$.pipe(
      map((event) => {
        switch (event.type) {
          case 'MATCH_FOUND':
            return LobbyActions.matchFound({ payload: event.payload as MatchFoundPayload });
          case 'INVITE_RECEIVED':
            return LobbyActions.inviteReceived({ invitation: event.payload as GameInvitation });
          case 'INVITE_CANCELLED':
            return LobbyActions.inviteCancelled({ invitationId: (event.payload as any).invitationId });
          case 'INVITE_DECLINED':
            return LobbyActions.inviteDeclined({ invitationId: (event.payload as any).invitationId });
          case 'INVITE_EXPIRED':
            return LobbyActions.inviteExpired({ invitationId: (event.payload as any).invitationId });
          default:
            return null;
        }
      }),
      filter((action): action is NonNullable<typeof action> => action !== null)
    )
  );

  // ── Navigation effects ─────────────────────────────────────────────────────

  navigateAfterMatch$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(LobbyActions.matchFound),
        tap(({ payload }) => {
          this.router.navigate(['/game', payload.gameId]);
        })
      ),
    { dispatch: false }
  );

  navigateAfterAccept$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(LobbyActions.respondToInvitationSuccess),
        filter(({ gameId }) => !!gameId),
        tap(({ gameId }) => {
          this.router.navigate(['/game', gameId]);
        })
      ),
    { dispatch: false }
  );
}
