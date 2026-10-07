// Looking back at a game: the board as it was after each move.
//
// The server sends the moves as coordinates plus notation, with no piece or capture
// information, so the positions are rebuilt by playing the moves from the starting position.
import { BoardState, Move, Piece, PieceColor, PieceType, Square } from '../models';

export type Squares = (Piece | null)[][];

const BACK_RANK: PieceType[] = ['rook', 'knight', 'bishop', 'queen', 'king', 'bishop', 'knight', 'rook'];
const PROMOTION_LETTER: Record<string, PieceType> = { Q: 'queen', R: 'rook', B: 'bishop', N: 'knight' };

export function startingSquares(): Squares {
  const rows: Squares = Array.from({ length: 8 }, () => Array<Piece | null>(8).fill(null));
  BACK_RANK.forEach((type, col) => {
    rows[0][col] = { type, color: 'black' };
    rows[1][col] = { type: 'pawn', color: 'black' };
    rows[6][col] = { type: 'pawn', color: 'white' };
    rows[7][col] = { type, color: 'white' };
  });
  return rows;
}

/** The position after `move`, without touching `squares`. */
export function applyMove(squares: Squares, move: Pick<Move, 'from' | 'to' | 'algebraicNotation'>): Squares {
  const next = squares.map((row) => [...row]);
  const { from, to } = move;
  const piece = next[from.row]?.[from.col];
  if (!piece) return next;

  // a pawn taking diagonally onto an empty square is en passant: the pawn taken sits beside it
  if (piece.type === 'pawn' && from.col !== to.col && !next[to.row][to.col]) next[from.row][to.col] = null;

  // the king moving two files is castling: the rook jumps over it
  if (piece.type === 'king' && Math.abs(to.col - from.col) === 2) {
    const kingside = to.col > from.col;
    next[from.row][kingside ? 5 : 3] = next[from.row][kingside ? 7 : 0];
    next[from.row][kingside ? 7 : 0] = null;
  }

  const promoted = /=([QRBN])/.exec(move.algebraicNotation ?? '')?.[1];
  next[to.row][to.col] = promoted ? { type: PROMOTION_LETTER[promoted], color: piece.color } : piece;
  next[from.row][from.col] = null;
  return next;
}

/**
 * The position before the first move, then after each one: `moves.length + 1` boards.
 * Null when the moves do not lead to `current`, e.g. a game that began from another position,
 * where looking back would show wrong boards.
 */
export function positionsOf(moves: Move[], current: BoardState | null): Squares[] | null {
  if (!current) return null;
  const positions: Squares[] = [startingSquares()];
  for (const move of moves) positions.push(applyMove(positions[positions.length - 1], move));
  return sameSquares(positions[positions.length - 1], current.squares) ? positions : null;
}

export function sameSquares(a: Squares, b: Squares): boolean {
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const x = a[r]?.[c] ?? null;
      const y = b[r]?.[c] ?? null;
      if (x?.type !== y?.type || x?.color !== y?.color) return false;
    }
  }
  return true;
}

/** Whose turn it is once `ply` moves have been played (White opens). */
export function sideToMoveAt(ply: number): PieceColor {
  return ply % 2 === 0 ? 'white' : 'black';
}

/** Where the king in check stands after `ply` moves, or null when the last move gave no check. */
export function checkedKingAt(squares: Squares, ply: number, lastMove: Pick<Move, 'isCheck'> | undefined): Square | null {
  if (!lastMove?.isCheck) return null;
  const color = sideToMoveAt(ply);
  for (let row = 0; row < 8; row++) {
    for (let col = 0; col < 8; col++) {
      const p = squares[row]?.[col];
      if (p?.type === 'king' && p.color === color) return { row, col };
    }
  }
  return null;
}
