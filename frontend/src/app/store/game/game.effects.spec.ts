import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { EMPTY, Observable, Subject, of, throwError } from 'rxjs';
import { ChatService } from '../../core/services/chat.service';
import { GameService } from '../../core/services/game.service';
import { WebSocketService } from '../../core/services/websocket.service';
import { Game } from '../../core/models';
import { makeChatMessage, makeGame } from '../../testing/game-fixtures';
import { GameActions } from './game.actions';
import { GameEffects, squareOf } from './game.effects';
import { selectAnalysis, selectBoard, selectCurrentGame, selectMovableColor } from './game.selectors';

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
    gameService = jasmine.createSpyObj<GameService>('GameService',
      ['resign', 'getAiMove', 'offerDraw', 'acceptDraw', 'declineDraw', 'undoMove', 'submitMove', 'createGame', 'getGame', 'evaluate']);
    chatService = jasmine.createSpyObj<ChatService>('ChatService', ['sendMessage', 'getMessages']);
    TestBed.configureTestingModule({
      providers: [
        GameEffects,
        provideMockActions(() => actions$),
        provideMockStore({ selectors: [{ selector: selectCurrentGame, value: makeGame() }, { selector: selectAnalysis, value: false }] }),
        provideRouter([]),
        { provide: GameService, useValue: gameService },
        { provide: ChatService, useValue: chatService },
        { provide: WebSocketService, useValue: socketService },
      ],
    });
    effects = TestBed.inject(GameEffects);
  });

  // overrideSelector changes the shared, module-level selectors: put them back for the next spec
  afterEach(() => TestBed.inject(MockStore).resetSelectors());

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

  // -- helpers for games with an AI -----------------------------------------------------------------
  const aiPlayer = (color: 'white' | 'black') =>
    ({ username: 'AI', color, timeRemainingMs: 0, capturedPieces: [], isAi: true });
  const movesPlayed = (n: number) => Array.from({ length: n }, () => ({}) as any);

  function showing(game: Game) {
    const store = TestBed.inject(MockStore);
    store.overrideSelector(selectCurrentGame, game);
    store.refreshState();
  }

  describe('triggerAiMove$', () => {
    const aiToMove = makeGame({ playerBlack: aiPlayer('black'), currentTurn: 'black' });

    it('asks for the AI move when a loaded game is waiting for it', () => {
      const out = collect(effects.triggerAiMove$);

      actions$.next(GameActions.loadGameSuccess({ game: aiToMove }));

      expect(out).toEqual([GameActions.requestAIMove()]);
    });

    it('does not ask when a game was just created: the page loads it next, and that asks', () => {
      const out = collect(effects.triggerAiMove$);

      actions$.next(GameActions.createGameSuccess({ game: aiToMove }));

      expect(out).toEqual([]);
    });

    it('asks again after an undo that leaves the AI on the move', () => {
      const out = collect(effects.triggerAiMove$);

      actions$.next(GameActions.undoMoveSuccess({ game: aiToMove }));

      expect(out).toEqual([GameActions.requestAIMove()]);
    });

    it('does not ask on the human\'s turn, in online games, or once the game is over', () => {
      const out = collect(effects.triggerAiMove$);

      actions$.next(GameActions.loadGameSuccess({ game: makeGame({ playerBlack: aiPlayer('black'), currentTurn: 'white' }) }));
      actions$.next(GameActions.loadGameSuccess({ game: { ...aiToMove, mode: 'online' } }));
      actions$.next(GameActions.loadGameSuccess({ game: { ...aiToMove, status: 'checkmate' } }));

      expect(out).toEqual([]);
    });
  });

  describe('requestAiMove$', () => {
    it('does not start a second request while one is running', () => {
      const first = new Subject<Game>();
      gameService.getAiMove.and.returnValue(first);
      collect(effects.requestAiMove$);

      actions$.next(GameActions.requestAIMove());
      actions$.next(GameActions.requestAIMove());

      expect(gameService.getAiMove).toHaveBeenCalledTimes(1);
    });

    it('stays quiet when the server says the AI is already working on it', () => {
      const busy = { status: 409, error: { type: 'https://chess-engine/errors/ai-busy', detail: 'The AI is already thinking' } };
      gameService.getAiMove.and.returnValue(throwError(() => busy));
      const out = collect(effects.requestAiMove$);

      actions$.next(GameActions.requestAIMove());

      expect(out).toEqual([]);   // that search's own answer, or the socket, delivers the move
    });

    it('reports any other failure with the server\'s explanation', () => {
      const failed = { status: 500, message: 'Http failure response', error: { detail: 'Something broke' } };
      gameService.getAiMove.and.returnValue(throwError(() => failed));
      const out = collect(effects.requestAiMove$);

      actions$.next(GameActions.requestAIMove());

      expect(out).toEqual([GameActions.aIMoveFailure({ error: 'Something broke' })]);
    });
  });

  describe('undoMove$', () => {
    const humanWhite = (extra: Partial<Game>) => makeGame({ mode: 'ai', playerBlack: aiPlayer('black'), ...extra });

    it('takes back the AI\'s reply with the player\'s move, in one step', () => {
      showing(humanWhite({ currentTurn: 'white', moves: movesPlayed(2) }));
      gameService.undoMove.and.returnValue(of(makeGame()));
      collect(effects.undoMove$);

      actions$.next(GameActions.undoMove());

      expect(gameService.undoMove).toHaveBeenCalledWith('game-1', 2);
    });

    it('takes back just the player\'s move while the AI has not answered yet', () => {
      showing(humanWhite({ currentTurn: 'black', moves: movesPlayed(1) }));
      gameService.undoMove.and.returnValue(of(makeGame()));
      collect(effects.undoMove$);

      actions$.next(GameActions.undoMove());

      expect(gameService.undoMove).toHaveBeenCalledWith('game-1', 1);
    });

    it('takes back one move when only the AI\'s opening move exists', () => {
      showing(makeGame({ mode: 'ai', playerWhite: aiPlayer('white'), currentTurn: 'black', moves: movesPlayed(1) }));
      gameService.undoMove.and.returnValue(of(makeGame()));
      collect(effects.undoMove$);

      actions$.next(GameActions.undoMove());

      expect(gameService.undoMove).toHaveBeenCalledWith('game-1', 1);
    });

    it('takes back one move in a local game', () => {
      showing(makeGame({ mode: 'local', currentTurn: 'white', moves: movesPlayed(4) }));
      gameService.undoMove.and.returnValue(of(makeGame()));
      collect(effects.undoMove$);

      actions$.next(GameActions.undoMove());

      expect(gameService.undoMove).toHaveBeenCalledWith('game-1', 1);
    });

    it('tells the player when it fails', () => {
      showing(makeGame({ mode: 'local', moves: movesPlayed(2) }));
      gameService.undoMove.and.returnValue(throwError(() => ({ error: { detail: 'No moves to undo' } })));
      const out = collect(effects.undoMove$);

      actions$.next(GameActions.undoMove());

      expect(out).toEqual([GameActions.requestFailed({ error: 'No moves to undo' })]);
    });
  });

  describe('submitMove$', () => {
    const move = { from: { row: 6, col: 4 }, to: { row: 4, col: 4 }, piece: { type: 'pawn' as const, color: 'white' as const } };

    it('sends one move at a time: a double click does not send it twice', () => {
      const pending = new Subject<Game>();
      gameService.submitMove.and.returnValue(pending);
      collect(effects.submitMove$);

      actions$.next(GameActions.submitMove({ move }));
      actions$.next(GameActions.submitMove({ move }));

      expect(gameService.submitMove).toHaveBeenCalledTimes(1);
    });

    it('reports a refused move with the server\'s explanation', () => {
      gameService.submitMove.and.returnValue(throwError(() => ({ error: { detail: 'Illegal move: e2e6' } })));
      const out = collect(effects.submitMove$);

      actions$.next(GameActions.submitMove({ move }));

      expect(out).toEqual([GameActions.submitMoveFailure({ error: 'Illegal move: e2e6' })]);
    });
  });

  describe('creating and loading', () => {
    it('shows the server\'s explanation when a game can\'t be created', () => {
      gameService.createGame.and.returnValue(throwError(() => ({ error: { detail: 'timeControl.type: must be blitz...' } })));
      const out = collect(effects.createGame$);

      actions$.next(GameActions.createGame({ options: {} as any }));

      expect(out).toEqual([GameActions.createGameFailure({ error: 'timeControl.type: must be blitz...' })]);
    });

    it('shows the server\'s explanation when a game can\'t be loaded', () => {
      gameService.getGame.and.returnValue(throwError(() => ({ error: { detail: 'Game not found: x' } })));
      const out = collect(effects.loadGame$);

      actions$.next(GameActions.loadGame({ gameId: 'x' }));

      expect(out).toEqual([GameActions.loadGameFailure({ error: 'Game not found: x' })]);
    });
  });


  describe('chat history', () => {
    it('loads the messages of a game when it is opened, so a reload does not empty the chat', () => {
      const earlier = [makeChatMessage({ id: 'm1' }), makeChatMessage({ id: 'm2' })];
      chatService.getMessages.and.returnValue(of(earlier));
      const out = collect(effects.loadChatHistory$);

      actions$.next(GameActions.loadGameSuccess({ game: makeGame({ id: 'game-7' }) }));

      expect(chatService.getMessages).toHaveBeenCalledWith('game-7');
      expect(out).toEqual([GameActions.loadChatMessagesSuccess({ messages: earlier })]);
    });

    it('opens the game with an empty chat if the history can\'t be fetched', () => {
      chatService.getMessages.and.returnValue(throwError(() => new Error('offline')));
      const out = collect(effects.loadChatHistory$);

      actions$.next(GameActions.loadGameSuccess({ game: makeGame() }));

      expect(out).toEqual([]);   // the game itself still works; chat just starts empty
    });
  });

  describe('analysis', () => {
    const evaluation = { score: 120, depth: 8, bestMove: 'g1f3' };
    const setAnalysis = (on: boolean) => {
      const store = TestBed.inject(MockStore);
      store.overrideSelector(selectAnalysis, on);
      store.refreshState();
    };

    it('asks for the evaluation when the analysis is turned on, not when it is turned off', () => {
      const out = collect(effects.askForEvaluationOnToggle$);

      setAnalysis(true);
      actions$.next(GameActions.toggleAnalysis());
      setAnalysis(false);
      actions$.next(GameActions.toggleAnalysis());

      expect(out).toEqual([GameActions.loadEvaluation()]);
    });

    it("asks again after every move, the AI's and the opponent's included, while the analysis is on", () => {
      const out = collect(effects.refreshEvaluationAfterMove$);
      const game = makeGame();
      setAnalysis(true);

      actions$.next(GameActions.submitMoveSuccess({ game }));
      actions$.next(GameActions.aIMoveSuccess({ game }));
      actions$.next(GameActions.receiveMove({ game }));
      actions$.next(GameActions.undoMoveSuccess({ game }));
      actions$.next(GameActions.loadGameSuccess({ game }));

      expect(out.length).toBe(5);
      expect(out.every((a) => a.type === GameActions.loadEvaluation.type)).toBeTrue();
    });

    it('does not ask when the analysis is off', () => {
      const out = collect(effects.refreshEvaluationAfterMove$);
      setAnalysis(false);

      actions$.next(GameActions.submitMoveSuccess({ game: makeGame() }));

      expect(out).toEqual([]);
    });

    it('shows what the server answers', () => {
      gameService.evaluate.and.returnValue(of(evaluation));
      const out = collect(effects.loadEvaluation$);

      actions$.next(GameActions.loadEvaluation());

      expect(gameService.evaluate).toHaveBeenCalledWith('game-1');
      expect(out).toEqual([GameActions.updateEvaluation({ evaluation })]);
    });

    it('says so when the server forbids it, and stays quiet about other failures', () => {
      const out = collect(effects.loadEvaluation$);

      gameService.evaluate.and.returnValue(throwError(() => ({ status: 403 })));
      actions$.next(GameActions.loadEvaluation());
      gameService.evaluate.and.returnValue(throwError(() => ({ status: 503 })));
      actions$.next(GameActions.loadEvaluation());

      expect(out).toEqual([
        GameActions.evaluationFailed({ forbidden: true }),
        GameActions.evaluationFailed({ forbidden: false }),
      ]);
    });

    it("turns a requested hint into the engine's best move", () => {
      gameService.evaluate.and.returnValue(of(evaluation));
      const out = collect(effects.requestHint$);

      actions$.next(GameActions.requestHint());

      expect(out).toEqual([GameActions.hintReady({ move: 'g1f3' })]);
    });

    it('tells the player when there is no move to suggest, or the request fails', () => {
      const out = collect(effects.requestHint$);

      gameService.evaluate.and.returnValue(of({ score: 0, depth: 1 }));
      actions$.next(GameActions.requestHint());
      gameService.evaluate.and.returnValue(throwError(() => ({ status: 403, error: { detail: 'No engine now' } })));
      actions$.next(GameActions.requestHint());

      expect(out.length).toBe(2);
      expect(out.every((a) => a.type === GameActions.requestFailed.type)).toBeTrue();
      expect((out[1] as any).error).toBe('No engine now');
    });

    describe('showing the hint', () => {
      const boardWith = (row: number, col: number, color: 'white' | 'black') => {
        const squares = Array.from({ length: 8 }, () => Array(8).fill(null));
        squares[row][col] = { type: 'knight', color };
        return { squares } as any;
      };

      it("picks up the piece the engine would move, when it is the player's", () => {
        const store = TestBed.inject(MockStore);
        store.overrideSelector(selectMovableColor, 'white');
        store.overrideSelector(selectBoard, boardWith(7, 6, 'white'));   // g1
        const out = collect(effects.showHint$);

        actions$.next(GameActions.hintReady({ move: 'g1f3' }));

        expect(out).toEqual([GameActions.selectSquare({ square: { row: 7, col: 6 } })]);
      });

      it("does not touch a piece the player can't move", () => {
        const store = TestBed.inject(MockStore);
        store.overrideSelector(selectMovableColor, null);
        store.overrideSelector(selectBoard, boardWith(7, 6, 'white'));
        const out = collect(effects.showHint$);

        actions$.next(GameActions.hintReady({ move: 'g1f3' }));

        expect(out).toEqual([]);
      });
    });

    describe('squareOf', () => {
      it("turns coordinates into the board's rows and columns, row 0 being rank 8", () => {
        expect(squareOf('a8')).toEqual({ row: 0, col: 0 });
        expect(squareOf('h1')).toEqual({ row: 7, col: 7 });
        expect(squareOf('e2e4')).toEqual({ row: 6, col: 4 });
      });

      it('refuses anything that is not a square', () => {
        expect(squareOf('')).toBeNull();
        expect(squareOf('z9')).toBeNull();
        expect(squareOf('e0')).toBeNull();
      });
    });
  });
});
