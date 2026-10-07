// src/app/store/game/game.reducer.ts
import { ActionReducer, createReducer, on } from '@ngrx/store';
import { isPlayableStatus } from '../../core/utils/game-status.utils';
import { needsPromotionChoice } from '../../core/utils/promotion.utils';
import { GameActions } from './game.actions';
import { GameState, initialGameState } from './game.state';

const movesPlayed = (state: GameState): number => state.currentGame?.moves.length ?? 0;

/** Show the board after `ply` moves; the last position is the live one. */
function reviewAt(state: GameState, ply: number): GameState {
  const total = movesPlayed(state);
  const at = Math.max(0, Math.min(total, ply));
  return {
    ...state, reviewPly: at >= total ? null : at,
    selectedSquare: null, legalMoves: [], legalMovesReady: false, pendingPromotion: null,
  };
}

const reducer = createReducer(
  initialGameState,

  on(GameActions.createGame, (state, { options }) => ({
    // "Analyse en temps réel" on the home page starts the game with the evaluation bar on
    ...initialGameState, savedGames: state.savedGames, isLoading: true, analysis: !!options.realTimeAnalysis,
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

  // The previous piece's moves must not pass for this one's while they load
  on(GameActions.selectSquare, (state, { square }) => ({
    ...state, selectedSquare: square, legalMoves: [], legalMovesReady: false,
  })),

  on(GameActions.clearSelection, (state) => ({
    ...state, selectedSquare: null, legalMoves: [], legalMovesReady: false,
  })),

  // An answer that arrives after the selection was cleared is for a piece nobody holds any more
  on(GameActions.loadLegalMovesSuccess, (state, { squares }) =>
    state.selectedSquare ? { ...state, legalMoves: squares ?? [], legalMovesReady: true } : state
  ),

  // A pawn reaching the last rank waits for the player to pick its piece
  on(GameActions.submitMove, (state, { move }) =>
    needsPromotionChoice(move)
      ? { ...state, pendingPromotion: move }
      : { ...state, pendingPromotion: null, notice: null, isLoading: true }
  ),

  // Looking back at the game: the board shows an earlier position and nothing can be moved on it
  on(GameActions.stepReview, (state, { delta }) => reviewAt(state, (state.reviewPly ?? movesPlayed(state)) + delta)),
  on(GameActions.reviewPly, (state, { ply }) => reviewAt(state, ply)),
  on(GameActions.endReview, (state) => ({ ...state, reviewPly: null })),

  on(GameActions.cancelPromotion, (state) => ({
    ...state, pendingPromotion: null, selectedSquare: null, legalMoves: [], legalMovesReady: false,
  })),

  // A move arriving over the socket may be the AI's: its search is over
  on(GameActions.submitMoveSuccess, GameActions.receiveMove,
    (state, { game }) => ({
      ...state, currentGame: game, isLoading: false, isAiThinking: false,
      selectedSquare: null, legalMoves: [], legalMovesReady: false, hint: null,
    })
  ),

  on(GameActions.submitMoveFailure, (state, { error }) => ({
    ...state, isLoading: false, error, notice: error,
    selectedSquare: null, legalMoves: [], legalMovesReady: false,
  })),

  on(GameActions.requestAIMove, (state) => ({
    ...state, isAiThinking: true,
  })),

  on(GameActions.aIMoveSuccess, (state, { game }) => ({
    ...state, currentGame: game, isAiThinking: false, hint: null,
  })),

  on(GameActions.aIMoveFailure, (state, { error }) => ({
    ...state, isAiThinking: false, error, notice: error,
  })),

  on(GameActions.requestFailed, (state, { error }) => ({
    ...state, isLoading: false, error, notice: error,
  })),

  on(GameActions.dismissNotice, (state) => ({ ...state, notice: null })),

  on(GameActions.gameUpdated, (state, { game }) => ({ ...state, currentGame: game, isAiThinking: false })),

  on(GameActions.undoMove, (state) => ({ ...state, selectedSquare: null, legalMoves: [], legalMovesReady: false })),

  on(GameActions.undoMoveSuccess, (state, { game }) => ({
    ...state, currentGame: game, selectedSquare: null, legalMoves: [], legalMovesReady: false, isAiThinking: false, hint: null,
  })),

  on(GameActions.updateEvaluation, (state, { evaluation }) => ({
    ...state, evaluation,
  })),

  // Turning the analysis off also takes the bar away
  on(GameActions.toggleAnalysis, (state) => ({
    ...state, analysis: !state.analysis, evaluation: state.analysis ? null : state.evaluation,
  })),

  on(GameActions.evaluationFailed, (state, { forbidden }) =>
    forbidden ? { ...state, analysis: false, evaluation: null } : state
  ),

  on(GameActions.hintReady, (state, { move }) => ({ ...state, hint: move })),

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
    // A called-off game has no result, so the result dialog never opens: say what happened here
    notice: game.status === 'aborted'
      ? "La partie a été annulée : personne n'a joué à temps. Aucun résultat, aucun changement de classement."
      : state.notice,
  })),

  on(GameActions.opponentDisconnected, (state, { color, until }) => ({
    ...state, opponentAway: { color, until },
  })),

  on(GameActions.opponentReconnected, (state) => ({ ...state, opponentAway: null })),

  on(GameActions.resetGame, () => initialGameState),
);

/** Looking back ends when the game moves on: a new move (or an undo) brings the live position back. */
export const gameReducer: ActionReducer<GameState> = (state, action) => {
  const next = reducer(state, action);
  const moved = movesPlayed(next) !== (state ? movesPlayed(state) : 0);
  return next.reviewPly !== null && moved ? { ...next, reviewPly: null } : next;
};
