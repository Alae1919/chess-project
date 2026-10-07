// ─────────────────────────────────────────────────────────────────────────────
// src/app/store/game/game.selectors.ts
// ─────────────────────────────────────────────────────────────────────────────
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { BoardState, Move, PieceColor, Square } from '../../core/models';
import { checkedKingAt, positionsOf } from '../../core/utils/move-history.utils';
import { playerColorOf } from '../../core/utils/game-result.utils';
import { selectUser } from '../account/account.reducer';
import { GameState } from './game.state';

const selectGameFeature = createFeatureSelector<GameState>('game');

export const selectCurrentGame       = createSelector(selectGameFeature, (s) => s.currentGame);
export const selectSavedGames        = createSelector(selectGameFeature, (s) => s.savedGames);
export const selectSelectedSquare    = createSelector(selectGameFeature, (s) => s.selectedSquare);
export const selectLegalMoves        = createSelector(selectGameFeature, (s) => s.legalMoves);
export const selectLegalMovesReady   = createSelector(selectGameFeature, (s) => s.legalMovesReady);
export const selectEvaluation        = createSelector(selectGameFeature, (s) => s.evaluation);
export const selectAnalysis          = createSelector(selectGameFeature, (s) => s.analysis);
export const selectHint              = createSelector(selectGameFeature, (s) => s.hint);
export const selectChatMessages      = createSelector(selectGameFeature, (s) => s.chatMessages);
export const selectOpponentAway      = createSelector(selectGameFeature, (s) => s.opponentAway);
export const selectNotice            = createSelector(selectGameFeature, (s) => s.notice);
export const selectPendingPromotion  = createSelector(selectGameFeature, (s) => s.pendingPromotion);
export const selectIsLoading         = createSelector(selectGameFeature, (s) => s.isLoading);
export const selectIsAiThinking      = createSelector(selectGameFeature, (s) => s.isAiThinking);
export const selectGameError         = createSelector(selectGameFeature, (s) => s.error);
export const selectBoard             = createSelector(selectCurrentGame, (g) => g?.board ?? null);
export const selectCurrentTurn       = createSelector(selectCurrentGame, (g) => g?.currentTurn ?? null);
export const selectMoveHistory       = createSelector(selectCurrentGame, (g) => g?.moves ?? []);
export const selectWhitePlayer       = createSelector(selectCurrentGame, (g) => g?.playerWhite ?? null);
export const selectBlackPlayer       = createSelector(selectCurrentGame, (g) => g?.playerBlack ?? null);
export const selectGameStatus        = createSelector(selectCurrentGame, (g) => g?.status ?? null);
export const selectOpening           = createSelector(selectCurrentGame, (g) => g?.opening ?? null);

// ── Looking back at the game ────────────────────────────────────────────────

/** The board after each move (index = moves played), or null when it can't be rebuilt */
export const selectPositions = createSelector(selectMoveHistory, selectBoard, (moves, board) => positionsOf(moves, board));

/** Looking back at the game is possible: there is a move to go back to */
export const selectCanReview = createSelector(selectPositions, (p) => !!p && p.length > 1);

/** Moves played on the board that is shown, or null for the live position */
export const selectReviewPly = createSelector(
  selectGameFeature, selectPositions,
  (s, positions) => (positions && s.reviewPly !== null ? Math.min(s.reviewPly, positions.length - 1) : null)
);

/** The move count of what the board shows: all of them when live */
export const selectViewPly = createSelector(selectReviewPly, selectMoveHistory, (ply, moves) => ply ?? moves.length);

/** What both boards draw: the live board, or the one being looked at */
export const selectDisplayedBoard = createSelector(
  selectReviewPly, selectPositions, selectBoard,
  (ply, positions, board): BoardState | null => (ply !== null && positions ? { squares: positions[ply] } : board)
);

/** The move that led to the board shown, for the last-move marks */
export const selectViewedMove = createSelector(
  selectViewPly, selectMoveHistory, (ply, moves): Move | null => moves[ply - 1] ?? null
);

/** The king in check on a past board; null on the live one, where the game's status says so */
export const selectReviewedCheck = createSelector(
  selectReviewPly, selectDisplayedBoard, selectMoveHistory,
  (ply, board, moves): Square | null => (ply !== null && board ? checkedKingAt(board.squares, ply, moves[ply - 1]) : null)
);

/**
 * The colour the viewer may pick up and move right now, or null: not their turn, a move is on
 * its way to the server, or the AI is thinking. In a local game one person plays both sides, so
 * it is whichever side is to move. Both boards use this, so neither lets a player touch the
 * opponent's pieces.
 */
export const selectMovableColor = createSelector(
  selectCurrentGame, selectUser, selectIsLoading, selectIsAiThinking, selectReviewPly,
  (game, user, loading, aiThinking, reviewing): PieceColor | null => {
    // an earlier position is for looking at, not for playing on
    if (!game || loading || aiThinking || reviewing !== null) return null;
    const mine = playerColorOf(game, user?.id);
    if (mine === null) {
      // null means "both seats" in a local game, but "unknown" online until the profile loads
      return game.mode === 'online' ? null : game.currentTurn;
    }
    return mine === game.currentTurn ? game.currentTurn : null;
  }
);
