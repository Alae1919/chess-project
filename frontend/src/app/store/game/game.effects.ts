// src/app/store/game/game.effects.ts
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { interval, of } from 'rxjs';
import { catchError, filter, map, switchMap, tap, withLatestFrom } from 'rxjs/operators';
import { GameActions } from './game.actions';
import { isPlayableStatus } from '../../core/utils/game-status.utils';
import { selectCurrentGame, selectSelectedSquare } from './game.selectors';
import { GameService } from '../../core/services/game.service';
import { ChatService } from '../../core/services/chat.service';
import { WebSocketService } from '../../core/services/websocket.service';
import { Router } from '@angular/router';

@Injectable()
export class GameEffects {
  private actions$ = inject(Actions);
  private store = inject(Store);
  private gameService = inject(GameService);
  private chatService = inject(ChatService);
  private wsService = inject(WebSocketService);
  private router = inject(Router);

  createGame$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.createGame),
      switchMap(({ options }) =>
        this.gameService.createGame(options).pipe(
          map((game) => GameActions.createGameSuccess({ game })),
          catchError((error) => of(GameActions.createGameFailure({ error: error.message })))
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
          catchError((error) => of(GameActions.loadGameFailure({ error: error.message })))
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
          this.wsService.messages$.subscribe((event) => {
            if (event.type === 'MOVE_MADE')
              this.store.dispatch(GameActions.receiveMove({ game: this.gameService.mapGame(event.payload) }));
            if (event.type === 'CHAT_MESSAGE')
              this.store.dispatch(GameActions.receiveChatMessage({ message: event.payload as any }));
            if (event.type === 'GAME_OVER')
              this.store.dispatch(GameActions.gameOver({ game: this.gameService.mapGame(event.payload) }));
          });
        })
      ),
    { dispatch: false }
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
      filter(([, game]) => !!game),
      switchMap(([{ move }, game]) =>
        this.gameService.submitMove(game!.id, move).pipe(
          map((updatedGame) => GameActions.submitMoveSuccess({ game: updatedGame })),
          catchError((error) => of(GameActions.submitMoveFailure({ error: error.message })))
        )
      )
    )
  );

  requestAiMove$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.requestAIMove),
      withLatestFrom(this.store.select(selectCurrentGame)),
      filter(([, game]) => !!game),
      switchMap(([, game]) =>
        this.gameService.getAiMove(game!.id).pipe(
          map((updatedGame) => GameActions.aIMoveSuccess({ game: updatedGame })),
          catchError(() => of(GameActions.createGameFailure({ error: 'AI move failed' })))
        )
      )
    )
  );

  triggerAiMove$ = createEffect(() =>
    this.actions$.pipe(
      ofType(GameActions.submitMoveSuccess, GameActions.createGameSuccess, GameActions.loadGameSuccess),
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
        this.gameService.undoMove(game!.id).pipe(
          map((updatedGame) => GameActions.undoMoveSuccess({ game: updatedGame })),
          catchError(() => of(GameActions.clearSelection()))
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
          catchError(() => of(GameActions.clearSelection()))
        )
      )
    )
  );

  resign$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(GameActions.resign),
        withLatestFrom(this.store.select(selectCurrentGame)),
        filter(([, game]) => !!game),
        switchMap(([, game]) => this.gameService.resign(game!.id))
      ),
    { dispatch: false }
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

  sendChatMessage$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(GameActions.sendChatMessage),
        withLatestFrom(this.store.select(selectCurrentGame)),
        filter(([, game]) => !!game),
        switchMap(([{ content }, game]) =>
          this.chatService.sendMessage(game!.id, content).pipe(
            map((msg) => this.store.dispatch(GameActions.receiveChatMessage({ message: msg })))
          )
        )
      ),
    { dispatch: false }
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
