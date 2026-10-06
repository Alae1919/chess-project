// src/app/store/game/game.effects.ts
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Action, Store } from '@ngrx/store';
import { EMPTY, interval, of, Subscription } from 'rxjs';
import { catchError, concatMap, exhaustMap, filter, map, switchMap, tap, withLatestFrom } from 'rxjs/operators';
import { GameActions } from './game.actions';
import { isPlayableStatus } from '../../core/utils/game-status.utils';
import { needsPromotionChoice } from '../../core/utils/promotion.utils';
import { selectCurrentGame, selectSelectedSquare } from './game.selectors';
import { GameService } from '../../core/services/game.service';
import { Game, PieceColor } from '../../core/models';
import { ChatService } from '../../core/services/chat.service';
import { WebSocketService } from '../../core/services/websocket.service';
import { Router } from '@angular/router';
import { errorMessage } from '../../core/utils/error-message';

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

@Injectable()
export class GameEffects {
  private actions$ = inject(Actions);
  private store = inject(Store);
  private gameService = inject(GameService);
  private chatService = inject(ChatService);
  private wsSub?: Subscription;
  private wsService = inject(WebSocketService);
  private router = inject(Router);

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
            if (event.type === 'MOVE_MADE')
              this.store.dispatch(GameActions.receiveMove({ game: this.gameService.mapGame(event.payload) }));
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
              this.store.dispatch(GameActions.gameOver({ game: this.gameService.mapGame(event.payload) }));
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

  selectSquare$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.selectSquare),
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => !!game),
      switchMap(([{ square }, game]) =>
        this.gameService.getLegalMoves(game!.id, square).pipe(
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
      exhaustMap(([, game]) =>
        this.gameService.getAiMove(game!.id).pipe(
          map((updatedGame) => GameActions.aIMoveSuccess({ game: updatedGame })),
          // Already searching (a request from before a reload, say): its move arrives on its
          // own, over the socket, so there is nothing to report
          catchError((error) => isAiBusy(error)
            ? EMPTY
            : of(GameActions.aIMoveFailure({ error: errorMessage(error) })))
        )
      )
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

  /** Tick the timer every second while a game is active */
  timer$ = createEffect(() =>
    interval(1000).pipe(
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => isPlayableStatus(game?.status)),
      map(() => GameActions.tickTimer())
    )
  );
}
