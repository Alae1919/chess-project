// ─────────────────────────────────────────────────────────────────────────────
// src/app/store/game/game.state.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Game, SavedGame, Square, PositionEvaluation, ChatMessage, Move, PieceColor } from '../../core/models';

export interface GameState {
  currentGame: Game | null;
  savedGames: SavedGame[];
  selectedSquare: Square | null;
  legalMoves: Square[];
  evaluation: PositionEvaluation | null;
  /** The player asked for the engine's opinion of the position after every move */
  analysis: boolean;
  /** The engine's suggested move for the position, as UCI text such as "e2e4", until the next move */
  hint: string | null;
  chatMessages: ChatMessage[];
  /** A pawn move onto the last rank, held until the player picks a piece */
  pendingPromotion: Omit<Move, 'algebraicNotation' | 'timestamp'> | null;
  /** The opponent has dropped out of an online game and forfeits at `until` unless they return */
  opponentAway: { color: PieceColor; until: number } | null;
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
  analysis: false,
  hint: null,
  chatMessages: [],
  pendingPromotion: null,
  opponentAway: null,
  notice: null,
  isLoading: false,
  isAiThinking: false,
  error: null,
};