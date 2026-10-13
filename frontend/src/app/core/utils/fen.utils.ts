// src/app/core/utils/fen.utils.ts
import { Piece, PieceType, Square } from '../models';

const TYPES: Record<string, PieceType> = { p: 'pawn', n: 'knight', b: 'bishop', r: 'rook', q: 'queen', k: 'king' };

/** The squares of a FEN's placement field, [row][col] with row 0 = rank 8 (as BoardState). */
export function fenToSquares(fen: string): (Piece | null)[][] {
  const rows = fen.trim().split(/\s+/)[0].split('/');
  if (rows.length !== 8) throw new Error(`Not a FEN placement: ${fen}`);
  return rows.map((row) => {
    const out: (Piece | null)[] = [];
    for (const ch of row) {
      if (/\d/.test(ch)) {
        for (let i = 0; i < Number(ch); i++) out.push(null);
      } else {
        const type = TYPES[ch.toLowerCase()];
        if (!type) throw new Error(`Not a FEN piece: ${ch}`);
        out.push({ type, color: ch === ch.toUpperCase() ? 'white' : 'black' });
      }
    }
    if (out.length !== 8) throw new Error(`Not a FEN rank: ${row}`);
    return out;
  });
}

/** "e2" → its square */
export function squareOf(name: string): Square {
  const col = name.charCodeAt(0) - 97;
  const rank = Number(name[1]);
  if (col < 0 || col > 7 || !(rank >= 1 && rank <= 8)) throw new Error(`Not a square: ${name}`);
  return { row: 8 - rank, col };
}

/** "e2e4" (or "e7e8q") → the squares it goes from and to */
export function uciSquares(uci: string): { from: Square; to: Square } {
  return { from: squareOf(uci.slice(0, 2)), to: squareOf(uci.slice(2, 4)) };
}

/** The full-move number and side to move of a FEN, to write a move as "5. Nxe5" or "5… dxe5" */
export function moveLabel(fen: string, san: string): string {
  const [, side, , , , full] = fen.trim().split(/\s+/);
  const n = Number(full) || 1;
  return side === 'b' ? `${n}… ${san}` : `${n}. ${san}`;
}
