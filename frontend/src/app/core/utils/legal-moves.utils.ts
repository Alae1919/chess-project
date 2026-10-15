// Where a piece can go, from the legal moves that come with every game
import { Square } from '../models';

/** "e2" → the square e2 (row 0 is rank 8, column 0 is file a) */
function square(alg: string): Square {
  return { col: alg.charCodeAt(0) - 97, row: 8 - Number(alg[1]) };
}

/**
 * The squares the piece on `from` can move to, read from the game's list of legal moves (UCI, for the side to move:
 * "e2e4", "e7e8q"). A promotion square counts once, whatever the piece. Null when the game came without a list,
 * which leaves it to the server (GET /legal-moves).
 */
export function legalTargets(legalMoves: readonly string[] | null | undefined, from: Square): Square[] | null {
  if (!Array.isArray(legalMoves)) return null;
  const origin = String.fromCharCode(97 + from.col) + (8 - from.row);
  const seen = new Set<string>();
  const targets: Square[] = [];
  for (const uci of legalMoves) {
    if (!uci.startsWith(origin)) continue;
    const to = uci.slice(2, 4);
    if (seen.has(to)) continue;
    seen.add(to);
    targets.push(square(to));
  }
  return targets;
}
