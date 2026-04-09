// src/app/store/game/game.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { GameActions } from './game.actions';
import { GameState, initialGameState } from './game.state';

export const gameReducer = createReducer(
  initialGameState,

  on(GameActions.createGame, GameActions.loadGame, (state) => ({
    ...state, isLoading: true, error: null,
  })),

  on(GameActions.createGameSuccess, GameActions.loadGameSuccess, (state, { game }) => ({
    ...state, currentGame: game, isLoading: false,
  })),

  on(GameActions.createGameFailure, GameActions.loadGameFailure, (state, { error }) => ({
    ...state, isLoading: false, error,
  })),

  on(GameActions.selectSquare, (state, { square }) => ({
    ...state, selectedSquare: square,
  })),

  on(GameActions.clearSelection, (state) => ({
    ...state, selectedSquare: null, legalMoves: [],
  })),

  on(GameActions.loadLegalMovesSuccess, (state, { squares }) => ({
    ...state, legalMoves: squares,
  })),

  on(GameActions.submitMove, (state) => ({
    ...state, isLoading: true,
  })),

  //on(GameActions.submitMoveSuccess, GameActions.receiveMoveSuccess ?? GameActions.receiveMove,
  on(GameActions.submitMoveSuccess, GameActions.receiveMove,  
    (state, { game }) => ({
      ...state, currentGame: game, isLoading: false,
      selectedSquare: null, legalMoves: [],
    })
  ),

  on(GameActions.submitMoveFailure, (state, { error }) => ({
    ...state, isLoading: false, error,
  })),

  on(GameActions.requestAIMove, (state) => ({
    ...state, isAiThinking: true,
  })),

  on(GameActions.aIMoveSuccess, (state, { game }) => ({
    ...state, currentGame: game, isAiThinking: false,
  })),

  on(GameActions.undoMoveSuccess, (state, { game }) => ({
    ...state, currentGame: game,
  })),

  on(GameActions.updateEvaluation, (state, { evaluation }) => ({
    ...state, evaluation,
  })),

  on(GameActions.loadSavedGamesSuccess, (state, { savedGames }) => ({
    ...state, savedGames,
  })),

  on(GameActions.loadChatMessagesSuccess, (state, { messages }) => ({
    ...state, chatMessages: messages,
  })),

  on(GameActions.receiveChatMessage, (state, { message }) => ({
    ...state, chatMessages: [...state.chatMessages, message],
  })),

  on(GameActions.tickTimer, (state) => {
    if (!state.currentGame || state.currentGame.status !== 'active') return state;
    const game = state.currentGame;
    const isWhiteTurn = game.currentTurn === 'white';
    return {
      ...state,
      currentGame: {
        ...game,
        playerWhite: {
          ...game.playerWhite,
          timeRemainingMs: isWhiteTurn
            ? Math.max(0, game.playerWhite.timeRemainingMs - 1000)
            : game.playerWhite.timeRemainingMs,
        },
        playerBlack: {
          ...game.playerBlack,
          timeRemainingMs: !isWhiteTurn
            ? Math.max(0, game.playerBlack.timeRemainingMs - 1000)
            : game.playerBlack.timeRemainingMs,
        },
      },
    };
  }),

  on(GameActions.gameOver, (state, { game }) => ({
    ...state, currentGame: game,
  })),

  on(GameActions.resetGame, () => initialGameState),
);


