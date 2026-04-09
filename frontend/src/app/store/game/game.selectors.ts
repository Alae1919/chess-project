// ─────────────────────────────────────────────────────────────────────────────
// src/app/store/game/game.selectors.ts
// ─────────────────────────────────────────────────────────────────────────────
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { GameState } from './game.state';

const selectGameFeature = createFeatureSelector<GameState>('game');

export const selectCurrentGame       = createSelector(selectGameFeature, (s) => s.currentGame);
export const selectSavedGames        = createSelector(selectGameFeature, (s) => s.savedGames);
export const selectSelectedSquare    = createSelector(selectGameFeature, (s) => s.selectedSquare);
export const selectLegalMoves        = createSelector(selectGameFeature, (s) => s.legalMoves);
export const selectEvaluation        = createSelector(selectGameFeature, (s) => s.evaluation);
export const selectChatMessages      = createSelector(selectGameFeature, (s) => s.chatMessages);
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
