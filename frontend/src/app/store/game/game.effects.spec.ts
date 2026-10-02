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

  beforeEach(() => {
    actions$ = new Subject<Action>();
    gameService = jasmine.createSpyObj<GameService>('GameService', ['resign', 'getAiMove']);
    chatService = jasmine.createSpyObj<ChatService>('ChatService', ['sendMessage']);
    TestBed.configureTestingModule({
      providers: [
        GameEffects,
        provideMockActions(() => actions$),
        provideMockStore({ selectors: [{ selector: selectCurrentGame, value: makeGame() }] }),
        provideRouter([]),
        { provide: GameService, useValue: gameService },
        { provide: ChatService, useValue: chatService },
        { provide: WebSocketService, useValue: jasmine.createSpyObj('WebSocketService', ['connect', 'disconnect']) },
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
