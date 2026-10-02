import { Move, PieceType } from '../models';

/** What a pawn can promote to, strongest first. */
export const PROMOTION_PIECES = ['queen', 'rook', 'bishop', 'knight'] as const;
export type PromotionPiece = (typeof PROMOTION_PIECES)[number];

/** Promotion suffix of a UCI move: e7e8q, e7e8n... */
export const UCI_PROMOTION_LETTER: Partial<Record<PieceType, string>> = {
  queen: 'q', rook: 'r', bishop: 'b', knight: 'n',
};

/**
 * A pawn move onto the last rank that doesn't name its piece yet.
 * Rows follow the board convention: 0 = rank 8, 7 = rank 1.
 */
export function needsPromotionChoice(move: Pick<Move, 'piece' | 'to' | 'promotion'>): boolean {
  return move.piece?.type === 'pawn' && (move.to.row === 0 || move.to.row === 7) && !move.promotion;
}
