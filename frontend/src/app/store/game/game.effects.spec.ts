import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { provideMockStore } from '@ngrx/store/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { ChatService } from '../../core/services/chat.service';
import { GameService } from '../../core/services/game.service';
import { WebSocketService } from '../../core/services/websocket.service';
import { makeChatMessage, makeGame } from '../../testing/game-fixtures';
import { GameActions } from './game.actions';
import { GameEffects } from './game.effects';
import { selectCurrentGame } from './game.selectors';

describe('GameEffects', () => {
  let actions$: Subject<Action>;
  let effects: GameEffects;
  let gameService: jasmine.SpyObj<GameService>;
  let chatService: jasmine.SpyObj<ChatService>;
  let reconnected$: Subject<void>;
  let socketService: unknown;

  beforeEach(() => {
    actions$ = new Subject<Action>();
    reconnected$ = new Subject<void>();
    socketService = Object.assign(jasmine.createSpyObj('WebSocketService', ['connect', 'disconnect']), { reconnected$ });
    gameService = jasmine.createSpyObj<GameService>('GameService', ['resign', 'getAiMove', 'offerDraw', 'acceptDraw', 'declineDraw']);
    chatService = jasmine.createSpyObj<ChatService>('ChatService', ['sendMessage']);
    TestBed.configureTestingModule({
      providers: [
        GameEffects,
        provideMockActions(() => actions$),
        provideMockStore({ selectors: [{ selector: selectCurrentGame, value: makeGame() }] }),
        provideRouter([]),
        { provide: GameService, useValue: gameService },
        { provide: ChatService, useValue: chatService },
        { provide: WebSocketService, useValue: socketService },
      ],
    });
    effects = TestBed.inject(GameEffects);
  });

  /** Subscribes to an effect and collects what it dispatches. */
  function collect(effect$: Observable<Action>): Action[] {
    const out: Action[] = [];
    effect$.subscribe((a) => out.push(a));
    return out;
  }

  it('reports a failed AI move so the thinking spinner can stop', () => {
    gameService.getAiMove.and.returnValue(throwError(() => new Error('timeout')));
    const out = collect(effects.requestAiMove$);

    actions$.next(GameActions.requestAIMove());

    expect(out).toEqual([GameActions.aIMoveFailure({ error: 'timeout' })]);
  });

  it('ends the game with the server response after resigning', () => {
    const finished = makeGame({ status: 'white_resigned' });
    gameService.resign.and.returnValue(of(finished));
    const out = collect(effects.resign$);

    actions$.next(GameActions.resign());

    expect(out).toEqual([GameActions.gameOver({ game: finished })]);
  });

  it('keeps handling resigns after one fails', () => {
    const finished = makeGame({ status: 'white_resigned' });
    gameService.resign.and.returnValues(throwError(() => new Error('offline')), of(finished));
    const out = collect(effects.resign$);

    actions$.next(GameActions.resign());
    actions$.next(GameActions.resign());

    expect(out).toEqual([
      GameActions.requestFailed({ error: 'offline' }),
      GameActions.gameOver({ game: finished }),
    ]);
  });

  describe('draw offers', () => {
    it('an offer the opponent can still answer updates the game', () => {
      const offered = makeGame({ drawOfferedBy: 'white' });
      gameService.offerDraw.and.returnValue(of(offered));
      const out = collect(effects.offerDraw$);

      actions$.next(GameActions.offerDraw());

      expect(out).toEqual([GameActions.gameUpdated({ game: offered })]);
    });

    it('an offer that ends the game (accepted) ends it in the store', () => {
      const drawn = makeGame({ status: 'draw_agreed', result: { reason: 'draw_agreement' } as any });
      gameService.offerDraw.and.returnValue(of(drawn));
      const out = collect(effects.offerDraw$);

      actions$.next(GameActions.offerDraw());

      expect(out).toEqual([GameActions.gameOver({ game: drawn })]);
    });

    it('shows the server explanation when the AI declines', () => {
      const declined = { error: { detail: 'The AI declined the draw offer.' }, message: 'Http failure response' };
      gameService.offerDraw.and.returnValue(throwError(() => declined));
      const out = collect(effects.offerDraw$);

      actions$.next(GameActions.offerDraw());

      expect(out).toEqual([GameActions.requestFailed({ error: 'The AI declined the draw offer.' })]);
    });

    it('accepting and declining call their own endpoints', () => {
      const drawn = makeGame({ status: 'draw_agreed', result: { reason: 'draw_agreement' } as any });
      const open = makeGame();
      gameService.acceptDraw.and.returnValue(of(drawn));
      gameService.declineDraw.and.returnValue(of(open));
      const out = collect(effects.drawResponse$);

      actions$.next(GameActions.drawResponse({ accepted: true }));
      actions$.next(GameActions.drawResponse({ accepted: false }));

      expect(out).toEqual([GameActions.gameOver({ game: drawn }), GameActions.gameUpdated({ game: open })]);
    });
  });

  it('reloads the current game once the socket is back', () => {
    const out = collect(effects.reloadAfterReconnect$);

    reconnected$.next();

    expect(out).toEqual([GameActions.loadGame({ gameId: 'game-1' })]);
  });

  it('adds a sent chat message, and survives a failed send', () => {
    const message = makeChatMessage();
    chatService.sendMessage.and.returnValues(throwError(() => new Error('offline')), of(message));
    const out = collect(effects.sendChatMessage$);

    actions$.next(GameActions.sendChatMessage({ content: 'hi' }));
    actions$.next(GameActions.sendChatMessage({ content: 'gl hf' }));

    expect(out).toEqual([
      GameActions.requestFailed({ error: 'offline' }),
      GameActions.receiveChatMessage({ message }),
    ]);
  });
});
