// ─────────────────────────────────────────────────────────────────────────────
// src/app/store/game/game.state.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Game, SavedGame, Square, PositionEvaluation, ChatMessage, Move } from '../../core/models';

export interface GameState {
  currentGame: Game | null;
  savedGames: SavedGame[];
  selectedSquare: Square | null;
  legalMoves: Square[];
  evaluation: PositionEvaluation | null;
  chatMessages: ChatMessage[];
  /** A pawn move onto the last rank, held until the player picks a piece */
  pendingPromotion: Omit<Move, 'algebraicNotation' | 'timestamp'> | null;
  /** A short message for the player, e.g. "the AI declined the draw" */
  notice: string | null;
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
  pendingPromotion: null,
  notice: null,
  isLoading: false,
  isAiThinking: false,
  error: null,
};