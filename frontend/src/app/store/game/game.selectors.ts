// ─────────────────────────────────────────────────────────────────────────────
// src/app/store/game/game.selectors.ts
// ─────────────────────────────────────────────────────────────────────────────
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { PieceColor } from '../../core/models';
import { playerColorOf } from '../../core/utils/game-result.utils';
import { selectUser } from '../account/account.reducer';
import { GameState } from './game.state';

const selectGameFeature = createFeatureSelector<GameState>('game');

export const selectCurrentGame       = createSelector(selectGameFeature, (s) => s.currentGame);
export const selectSavedGames        = createSelector(selectGameFeature, (s) => s.savedGames);
export const selectSelectedSquare    = createSelector(selectGameFeature, (s) => s.selectedSquare);
export const selectLegalMoves        = createSelector(selectGameFeature, (s) => s.legalMoves);
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

/**
 * The colour the viewer may pick up and move right now, or null: not their turn, a move is on
 * its way to the server, or the AI is thinking. In a local game one person plays both sides, so
 * it is whichever side is to move. Both boards use this, so neither lets a player touch the
 * opponent's pieces.
 */
export const selectMovableColor = createSelector(
  selectCurrentGame, selectUser, selectIsLoading, selectIsAiThinking,
  (game, user, loading, aiThinking): PieceColor | null => {
    if (!game || loading || aiThinking) return null;
    const mine = playerColorOf(game, user?.id);
    if (mine === null) {
      // null means "both seats" in a local game, but "unknown" online until the profile loads
      return game.mode === 'online' ? null : game.currentTurn;
    }
    return mine === game.currentTurn ? game.currentTurn : null;
  }
);
