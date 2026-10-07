// Letting go of a piece on a board: may the move go ahead?
import { Game, PieceColor, Square } from '../models';
import { isPlayableStatus } from './game-status.utils';

/** What a board knows about the player's hand at the moment of the drop */
export interface DropContext {
  game: Pick<Game, 'status'> | null;
  /** The side the player may move right now (null: none) */
  movable: PieceColor | null;
  selected: Square | null;
  legalMoves: Square[];
  /** `legalMoves` is the answer for `selected` (an empty list can also mean "not here yet") */
  legalMovesReady: boolean;
}

/**
 * accept: legal, play it · reject: not legal, the piece goes back and nothing counts ·
 * wait: the legal moves of the piece have not arrived yet, decide when they do
 */
export type DropVerdict = 'accept' | 'reject' | 'wait';

export const sameSquare = (a: Square | null, b: Square | null): boolean =>
  !!a && !!b && a.row === b.row && a.col === b.col;

export function dropVerdict(ctx: DropContext, from: Square, to: Square): DropVerdict {
  if (!ctx.game || !isPlayableStatus(ctx.game.status) || !ctx.movable) return 'reject';
  if (sameSquare(from, to) || !sameSquare(ctx.selected, from)) return 'reject';
  if (!ctx.legalMovesReady) return 'wait';
  return ctx.legalMoves.some((m) => sameSquare(m, to)) ? 'accept' : 'reject';
}

/** Pixels a pointer must travel before a press on a piece becomes a drag (below that it is a tap) */
export const DRAG_THRESHOLD_PX = 6;
