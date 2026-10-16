// src/app/store/game/game.effects.ts
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Action, Store } from '@ngrx/store';
import { EMPTY, interval, of, Subscription, timer } from 'rxjs';
import { catchError, concatMap, delayWhen, distinctUntilChanged, exhaustMap, filter, map, switchMap, take, takeUntil, tap, withLatestFrom } from 'rxjs/operators';
import { GameActions } from './game.actions';
import { isPlayableStatus } from '../../core/utils/game-status.utils';
import { needsPromotionChoice } from '../../core/utils/promotion.utils';
import { selectAnalysis, selectBoard, selectCurrentGame, selectMovableColor, selectSelectedSquare } from './game.selectors';
import { GameService } from '../../core/services/game.service';
import { Game, PieceColor, Square } from '../../core/models';
import { ChatService } from '../../core/services/chat.service';
import { WebSocketService } from '../../core/services/websocket.service';
import { NavigationEnd, Router } from '@angular/router';
import { TelemetryService, timed } from '../../core/telemetry/telemetry.service';
import { errorMessage } from '../../core/utils/error-message';

/**
 * The AI's move is shown no sooner than this after it was asked for, so the player sees it come rather than
 * finding it already played. The server answers as soon as it has the move; the wait is the page's.
 */
export const AI_MIN_REPLY_MS = 600;

/** The server refused because the AI is already working out this game's move. */
function isAiBusy(error: any): boolean {
  return error?.status === 409 && String(error?.error?.type ?? '').endsWith('/ai-busy');
}

/**
 * How many moves "undo" takes back. Against the AI the player takes back their own move
 * together with the AI's reply (2), once it is their turn again; otherwise just the last one.
 */
function pliesToUndo(game: Game): number {
  const aiColor = game.playerWhite?.isAi ? 'white' : game.playerBlack?.isAi ? 'black' : null;
  const playersTurnAgainstAi = game.mode === 'ai' && aiColor !== null && game.currentTurn !== aiColor;
  return playersTurnAgainstAi && game.moves.length >= 2 ? 2 : 1;
}

/** "e2e4" -> the square e2, in the board's rows (row 0 is rank 8) and columns (column 0 is file a). */
export function squareOf(uci: string): Square | null {
  const col = uci.charCodeAt(0) - 'a'.charCodeAt(0);
  const rank = Number(uci[1]);
  return col >= 0 && col < 8 && rank >= 1 && rank <= 8 ? { row: 8 - rank, col } : null;
}

@Injectable()
export class GameEffects {
  private actions$ = inject(Actions);
  private store = inject(Store);
  private gameService = inject(GameService);
  private chatService = inject(ChatService);
  private wsSub?: Subscription;
  private wsService = inject(WebSocketService);
  private router = inject(Router);
  private telemetry = inject(TelemetryService);
  /** When the AI's move may be shown (performance.now()): AI_MIN_REPLY_MS after it was asked for */
  private aiShowAt = 0;

  createGame$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.createGame),
      switchMap(({ options }) =>
        this.gameService.createGame(options).pipe(
          map((game) => GameActions.createGameSuccess({ game })),
          catchError((error) => of(GameActions.createGameFailure({ error: errorMessage(error) })))
        )
      )
    )
  );

  /** From "start a game" to the game page being on screen (see TelemetryService). */
  timeGameStart$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(GameActions.createGame),
        switchMap(() => {
          const start = performance.now();
          return this.actions$.pipe(
            ofType(GameActions.createGameSuccess),
            take(1),
            switchMap(({ game }) => this.router.events.pipe(
              filter((e): e is NavigationEnd => e instanceof NavigationEnd && e.urlAfterRedirects === `/game/${game.id}`),
              take(1),
            )),
            takeUntil(this.actions$.pipe(ofType(GameActions.createGameFailure))),
            tap(() => this.telemetry.timing('game.start', performance.now() - start)),
          );
        })
      ),
    { dispatch: false }
  );

  navigateAfterCreateOrLoad$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(GameActions.createGameSuccess, GameActions.loadGameSuccess),
        tap(({ game }) => {
          const targetUrl = `/game/${game.id}`;
          if (this.router.url !== targetUrl) {
            this.router.navigate(['/game', game.id]);
          }
        })
      ),
    { dispatch: false }
  );

  loadGame$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.loadGame),
      switchMap(({ gameId }) =>
        this.gameService.getGame(gameId).pipe(
          map((game) => GameActions.loadGameSuccess({ game })),
          catchError((error) => of(GameActions.loadGameFailure({ error: errorMessage(error) })))
        )
      )
    )
  );

  connectWsAfterLoad$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(GameActions.loadGameSuccess),
        tap(({ game }) => {
          this.wsService.connect(game.id);
          this.wsSub?.unsubscribe(); // loadGameSuccess fires more than once per game
          this.wsSub = this.wsService.messages$.subscribe((event) => {
            // the AI's move may come over the socket before the HTTP answer: it waits just the same
            if (event.type === 'MOVE_MADE')
              this.afterAiPause(() => this.store.dispatch(GameActions.receiveMove({ game: this.gameService.mapGame(event.payload) })));
            if (event.type === 'CHAT_MESSAGE')
              this.store.dispatch(GameActions.receiveChatMessage({ message: event.payload as any }));
            if (event.type === 'DRAW_OFFERED' || event.type === 'DRAW_DECLINED')
              this.store.dispatch(GameActions.gameUpdated({ game: this.gameService.mapGame(event.payload) }));
            if (event.type === 'OPPONENT_DISCONNECTED') {
              const { color, timeoutMs } = event.payload as { color: PieceColor; timeoutMs: number };
              this.store.dispatch(GameActions.opponentDisconnected({ color, until: Date.now() + timeoutMs }));
            }
            if (event.type === 'OPPONENT_RECONNECTED')
              this.store.dispatch(GameActions.opponentReconnected());
            if (event.type === 'GAME_OVER')
              this.afterAiPause(() => this.store.dispatch(GameActions.gameOver({ game: this.gameService.mapGame(event.payload) })));
          });
        })
      ),
    { dispatch: false }
  );

  // After a dropped connection: fetch what was missed (moves, a result, a draw offer)
  reloadAfterReconnect$ = createEffect(() =>
    this.wsService.reconnected$.pipe(
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => !!game),
      map(([, game]) => GameActions.loadGame({ gameId: game!.id }))
    )
  );

  // The chat is not part of the game payload: without this, a reload or a reconnect empties it
  loadChatHistory$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.loadGameSuccess),
      switchMap(({ game }) =>
        this.chatService.getMessages(game.id).pipe(
          map((messages) => GameActions.loadChatMessagesSuccess({ messages })),
          catchError(() => EMPTY) // the game works without it; the chat just starts empty
        )
      )
    )
  );

  // ── Analysis: the engine's opinion of the position, kept up to date while the player wants it ──

  askForEvaluationOnToggle$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.toggleAnalysis),
      withLatestFrom(this.store.select(selectAnalysis)),
      filter(([, on]) => on),
      map(() => GameActions.loadEvaluation())
    )
  );

  refreshEvaluationAfterMove$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.loadGameSuccess, GameActions.submitMoveSuccess, GameActions.receiveMove,
             GameActions.aIMoveSuccess, GameActions.undoMoveSuccess),
      withLatestFrom(this.store.select(selectAnalysis)),
      filter(([, on]) => on),
      map(() => GameActions.loadEvaluation())
    )
  );

  loadEvaluation$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.loadEvaluation),
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => !!game),
      // switchMap: only the newest position's answer matters
      switchMap(([, game]) =>
        this.gameService.evaluate(game!.id).pipe(
          map((evaluation) => GameActions.updateEvaluation({ evaluation })),
          catchError((error) => of(GameActions.evaluationFailed({ forbidden: error?.status === 403 })))
        )
      )
    )
  );

  requestHint$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.requestHint),
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => !!game),
      switchMap(([, game]) =>
        this.gameService.evaluate(game!.id).pipe(
          map((evaluation) => evaluation.bestMove
            ? GameActions.hintReady({ move: evaluation.bestMove })
            : GameActions.requestFailed({ error: 'Aucun coup à suggérer.' })),
          catchError((error) => of(GameActions.requestFailed({ error: errorMessage(error) })))
        )
      )
    )
  );

  // Pick up the piece the engine would move, so either board shows where it can go
  showHint$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.hintReady),
      withLatestFrom(this.store.select(selectMovableColor), this.store.select(selectBoard)),
      map(([{ move }, movable, board]) => {
        const from = squareOf(move);
        const piece = from ? (board as any)?.squares?.[from.row]?.[from.col] : null;
        return from && piece && piece.color === movable ? GameActions.selectSquare({ square: from }) : null;
      }),
      filter((action): action is ReturnType<typeof GameActions.selectSquare> => action !== null)
    )
  );

  selectSquare$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.selectSquare),
      withLatestFrom(this.store.select(selectCurrentGame)),
      // a game carries its legal moves, and the reducer has them already: only a game without asks the server
      filter(([, game]) => !!game && !Array.isArray(game.legalMoves)),
      switchMap(([{ square }, game]) =>
        this.gameService.getLegalMoves(game!.id, square).pipe(
          timed(this.telemetry, 'legal.select'),
          map((squares) => GameActions.loadLegalMovesSuccess({ squares })),
          catchError(() => of(GameActions.clearSelection()))
        )
      )
    )
  );

  submitMove$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.submitMove),
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([{ move }, game]) => !!game && !needsPromotionChoice(move)),
      // exhaustMap: a double click must not send the move twice, and switchMap would drop the
      // first answer, which in an AI game is what asks the AI to reply
      exhaustMap(([{ move }, game]) =>
        this.gameService.submitMove(game!.id, move).pipe(
          timed(this.telemetry, 'move.roundtrip'),
          map((updatedGame) => GameActions.submitMoveSuccess({ game: updatedGame })),
          catchError((error) => of(GameActions.submitMoveFailure({ error: errorMessage(error) })))
        )
      )
    )
  );

  requestAiMove$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.requestAIMove),
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => !!game),
      // exhaustMap: while the server is searching, a second request only repeats the work
      exhaustMap(([, game]) => {
        this.aiShowAt = performance.now() + AI_MIN_REPLY_MS;
        return this.gameService.getAiMove(game!.id).pipe(
          timed(this.telemetry, 'ai.reply'),
          delayWhen(() => timer(Math.max(0, this.aiShowAt - performance.now()))),
          map((updatedGame) => GameActions.aIMoveSuccess({ game: updatedGame })),
          // Already searching (a request from before a reload, say): its move arrives on its
          // own, over the socket, so there is nothing to report
          catchError((error) => isAiBusy(error)
            ? EMPTY
            : of(GameActions.aIMoveFailure({ error: errorMessage(error) })))
        );
      })
    )
  );

  triggerAiMove$ = createEffect(() =>
    this.actions$.pipe(
      // Not createGameSuccess: the game page loads the new game next, and that asks. Asking
      // here too sent two requests for the same move.
      ofType(GameActions.submitMoveSuccess, GameActions.loadGameSuccess, GameActions.undoMoveSuccess),
      filter(({ game }) => {
        if (game.mode === 'online') return false; // online games never trigger AI
        const isAiTurn =
          (game.playerWhite?.isAi && game.currentTurn === 'white') ||
          (game.playerBlack?.isAi && game.currentTurn === 'black');
        return !!isAiTurn && isPlayableStatus(game.status);
      }),
      map(() => GameActions.requestAIMove())
    )
  );

  undoMove$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.undoMove),
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => !!game),
      switchMap(([, game]) =>
        this.gameService.undoMove(game!.id, pliesToUndo(game!)).pipe(
          map((updatedGame) => GameActions.undoMoveSuccess({ game: updatedGame })),
          catchError((error) => of(GameActions.requestFailed({ error: errorMessage(error) })))
        )
      )
    )
  );

  saveGame$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.saveGame),
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => !!game),
      switchMap(([, game]) =>
        this.gameService.saveGame(game!.id).pipe(
          map((savedGame) => GameActions.saveGameSuccess({ savedGame })),
          catchError((error) => of(GameActions.requestFailed({ error: errorMessage(error) })))
        )
      )
    )
  );

  // The finished game comes back in the response; the GAME_OVER broadcast may also arrive
  resign$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.resign),
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => !!game),
      switchMap(([, game]) =>
        this.gameService.resign(game!.id).pipe(
          map((finished) => GameActions.gameOver({ game: finished })),
          catchError((error) => of(GameActions.requestFailed({ error: errorMessage(error) })))
        )
      )
    )
  );

  // The opponent's answer comes back as a game that is over (agreed) or still open (declined)
  private afterDraw(game: Game): Action {
    return game.result ? GameActions.gameOver({ game }) : GameActions.gameUpdated({ game });
  }

  offerDraw$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.offerDraw),
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => !!game),
      switchMap(([, game]) =>
        this.gameService.offerDraw(game!.id).pipe(
          map((updated) => this.afterDraw(updated)),
          catchError((error) => of(GameActions.requestFailed({ error: errorMessage(error) })))
        )
      )
    )
  );

  drawResponse$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.drawResponse),
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => !!game),
      switchMap(([{ accepted }, game]) =>
        (accepted ? this.gameService.acceptDraw(game!.id) : this.gameService.declineDraw(game!.id)).pipe(
          map((updated) => this.afterDraw(updated)),
          catchError((error) => of(GameActions.requestFailed({ error: errorMessage(error) })))
        )
      )
    )
  );

  loadSavedGames$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.loadSavedGames),
      switchMap(() =>
        this.gameService.getSavedGames().pipe(
          map((savedGames) => GameActions.loadSavedGamesSuccess({ savedGames })),
          catchError(() => of(GameActions.loadSavedGamesSuccess({ savedGames: [] })))
        )
      )
    )
  );

  // concatMap: quick successive messages are all sent, in order
  sendChatMessage$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.sendChatMessage),
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => !!game),
      concatMap(([{ content }, game]) =>
        this.chatService.sendMessage(game!.id, content).pipe(
          map((message) => GameActions.receiveChatMessage({ message })),
          catchError((error) => of(GameActions.requestFailed({ error: error.message })))
        )
      )
    )
  );

  /**
   * Tick the timer every second while a game with a clock is in play, and only then: each tick is a change detection
   * of the whole page, and the reducer ignores it in AI and unlimited games.
   */
  timer$ = createEffect(() =>
    this.store.select(selectCurrentGame).pipe(
      map((game) => isPlayableStatus(game?.status) && game?.mode !== 'ai' && game?.timeControl?.type !== 'unlimited'),
      distinctUntilChanged(),
      switchMap((playing) => (playing ? interval(1000).pipe(map(() => GameActions.tickTimer())) : EMPTY))
    )
  );

  /** Runs `show` once the AI's move may be shown (at once if no AI move is pending) */
  private afterAiPause(show: () => void): void {
    const wait = this.aiShowAt - performance.now();
    if (wait > 0) setTimeout(show, wait);
    else show();
  }
}
