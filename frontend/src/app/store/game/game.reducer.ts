// src/app/store/game/game.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { isPlayableStatus } from '../../core/utils/game-status.utils';
import { needsPromotionChoice } from '../../core/utils/promotion.utils';
import { GameActions } from './game.actions';
import { GameState, initialGameState } from './game.state';

export const gameReducer = createReducer(
  initialGameState,

  on(GameActions.createGame, (state) => ({
    ...initialGameState, savedGames: state.savedGames, isLoading: true,
  })),

  on(GameActions.loadGame, (state, { gameId }) => ({
    // Switching to another game: drop the previous game's chat, selection and evaluation
    ...(state.currentGame?.id === gameId ? state : { ...initialGameState, savedGames: state.savedGames }),
    isLoading: true, error: null,
  })),

  on(GameActions.createGameSuccess, GameActions.loadGameSuccess, (state, { game }) => ({
    ...state, currentGame: game, isLoading: false,
  })),

  // A failed request is told to the player (the notice), not just kept in the store
  on(GameActions.createGameFailure, GameActions.loadGameFailure, (state, { error }) => ({
    ...state, isLoading: false, error, notice: error,
  })),

  on(GameActions.selectSquare, (state, { square }) => ({
    ...state, selectedSquare: square,
  })),

  on(GameActions.clearSelection, (state) => ({
    ...state, selectedSquare: null, legalMoves: [],
  })),

  on(GameActions.loadLegalMovesSuccess, (state, { squares }) => ({
    ...state, legalMoves: squares ?? [],
  })),

  // A pawn reaching the last rank waits for the player to pick its piece
  on(GameActions.submitMove, (state, { move }) =>
    needsPromotionChoice(move)
      ? { ...state, pendingPromotion: move }
      : { ...state, pendingPromotion: null, notice: null, isLoading: true }
  ),

  on(GameActions.cancelPromotion, (state) => ({
    ...state, pendingPromotion: null, selectedSquare: null, legalMoves: [],
  })),

  // A move arriving over the socket may be the AI's: its search is over
  on(GameActions.submitMoveSuccess, GameActions.receiveMove,
    (state, { game }) => ({
      ...state, currentGame: game, isLoading: false, isAiThinking: false,
      selectedSquare: null, legalMoves: [],
    })
  ),

  on(GameActions.submitMoveFailure, (state, { error }) => ({
    ...state, isLoading: false, error, notice: error,
    selectedSquare: null, legalMoves: [],
  })),

  on(GameActions.requestAIMove, (state) => ({
    ...state, isAiThinking: true,
  })),

  on(GameActions.aIMoveSuccess, (state, { game }) => ({
    ...state, currentGame: game, isAiThinking: false,
  })),

  on(GameActions.aIMoveFailure, (state, { error }) => ({
    ...state, isAiThinking: false, error, notice: error,
  })),

  on(GameActions.requestFailed, (state, { error }) => ({
    ...state, isLoading: false, error, notice: error,
  })),

  on(GameActions.dismissNotice, (state) => ({ ...state, notice: null })),

  on(GameActions.gameUpdated, (state, { game }) => ({ ...state, currentGame: game, isAiThinking: false })),

  on(GameActions.undoMove, (state) => ({ ...state, selectedSquare: null, legalMoves: [] })),

  on(GameActions.undoMoveSuccess, (state, { game }) => ({
    ...state, currentGame: game, selectedSquare: null, legalMoves: [], isAiThinking: false,
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
    // The sender gets its own message twice (HTTP response + WebSocket broadcast): dedupe by id
    ...state,
    chatMessages: state.chatMessages.some((m) => m.id === message.id)
      ? state.chatMessages
      : [...state.chatMessages, message],
  })),

  // The server owns the clocks; between its updates the display counts down from the
  // last snapshot. Nothing runs in unlimited games or before the first move.
  on(GameActions.tickTimer, (state) => {
    if (!state.currentGame || !isPlayableStatus(state.currentGame.status)) return state;
    const game = state.currentGame;
    if (game.timeControl.type === 'unlimited' || game.moves.length === 0) return state;
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
    ...state, currentGame: game, opponentAway: null, isAiThinking: false,
  })),

  on(GameActions.opponentDisconnected, (state, { color, until }) => ({
    ...state, opponentAway: { color, until },
  })),

  on(GameActions.opponentReconnected, (state) => ({ ...state, opponentAway: null })),

  on(GameActions.resetGame, () => initialGameState),
);


