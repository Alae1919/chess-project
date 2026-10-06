import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { Observable, Subject, of, throwError } from 'rxjs';
import { GameInvitation } from '../../core/models';
import { InvitationService } from '../../core/services/invitation.service';
import { LobbyWebSocketService } from '../../core/services/lobby-websocket.service';
import { MatchmakingService } from '../../core/services/matchmaking.service';
import { LobbyActions } from './lobby.actions';
import { LobbyEffects } from './lobby.effects';

describe('LobbyEffects', () => {
  let actions$: Subject<Action>;
  let effects: LobbyEffects;
  let matchmaking: jasmine.SpyObj<MatchmakingService>;
  let invitations: jasmine.SpyObj<InvitationService>;

  beforeEach(() => {
    actions$ = new Subject<Action>();
    matchmaking = jasmine.createSpyObj<MatchmakingService>('MatchmakingService', ['joinQueue', 'leaveQueue']);
    invitations = jasmine.createSpyObj<InvitationService>('InvitationService',
      ['sendInvitation', 'respondToInvitation', 'cancelInvitation', 'getPendingInvitations']);
    TestBed.configureTestingModule({
      providers: [
        LobbyEffects,
        provideMockActions(() => actions$),
        provideRouter([]),
        { provide: MatchmakingService, useValue: matchmaking },
        { provide: InvitationService, useValue: invitations },
        { provide: LobbyWebSocketService, useValue: { messages$: new Subject() } },
      ],
    });
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    effects = TestBed.inject(LobbyEffects);
  });

  function collect(effect$: Observable<Action>): Action[] {
    const out: Action[] = [];
    effect$.subscribe((a) => out.push(a));
    return out;
  }

  const refusal = (detail: string) => ({ message: 'Http failure response for /api/x: 409 Conflict', error: { detail } });

  it('says why joining the queue failed, in the server\'s words', () => {
    matchmaking.joinQueue.and.returnValue(throwError(() => refusal('must be blitz, rapid, classical or unlimited')));
    const out = collect(effects.joinQueue$);

    actions$.next(LobbyActions.joinQueue({ req: {} as any }));

    expect(out).toEqual([LobbyActions.joinQueueFailure({ error: 'must be blitz, rapid, classical or unlimited' })]);
  });

  it('says why a challenge could not be sent', () => {
    invitations.sendInvitation.and.returnValue(throwError(() => refusal('User not found: zed')));
    const out = collect(effects.sendInvitation$);

    actions$.next(LobbyActions.sendInvitation({ req: {} as any }));

    expect(out).toEqual([LobbyActions.sendInvitationFailure({ error: 'User not found: zed' })]);
  });

  it('names the invitation that was accepted, and the game it started', () => {
    invitations.respondToInvitation.and.returnValue(of({ id: 'game-9' } as any));
    const out = collect(effects.respondToInvitation$);

    actions$.next(LobbyActions.respondToInvitation({ invitationId: 'inv-1', response: 'accept' }));

    expect(out).toEqual([LobbyActions.respondToInvitationSuccess({ invitationId: 'inv-1', gameId: 'game-9' })]);
  });

  it('names the invitation that was declined, with no game', () => {
    invitations.respondToInvitation.and.returnValue(of({} as GameInvitation as any));
    const out = collect(effects.respondToInvitation$);

    actions$.next(LobbyActions.respondToInvitation({ invitationId: 'inv-1', response: 'decline' }));

    expect(out).toEqual([LobbyActions.respondToInvitationSuccess({ invitationId: 'inv-1', gameId: undefined })]);
  });

  it('names the invitation that could not be answered, and why', () => {
    invitations.respondToInvitation.and.returnValue(throwError(() => refusal('Invitation is no longer pending')));
    const out = collect(effects.respondToInvitation$);

    actions$.next(LobbyActions.respondToInvitation({ invitationId: 'inv-1', response: 'accept' }));

    expect(out).toEqual([
      LobbyActions.respondToInvitationFailure({ invitationId: 'inv-1', error: 'Invitation is no longer pending' }),
    ]);
  });
});
