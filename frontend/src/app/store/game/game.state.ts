// ─────────────────────────────────────────────────────────────────────────────
// src/app/store/game/game.state.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Game, SavedGame, Square, PositionEvaluation, ChatMessage } from '../../core/models';

export interface GameState {
  currentGame: Game | null;
  savedGames: SavedGame[];
  selectedSquare: Square | null;
  legalMoves: Square[];
  evaluation: PositionEvaluation | null;
  chatMessages: ChatMessage[];
  isLoading: boolean;
  isAiThinking: boolean;
  error: string | null;
}

export const initialGameState: GameState = {
  currentGame: null,
  savedGames: [],
  selectedSquare: null,
  legalMoves: [],
  evaluation: null,
  chatMessages: [],
  isLoading: false,
  isAiThinking: false,
  error: null,
};