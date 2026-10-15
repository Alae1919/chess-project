// Playing a move on the client's copy of the game, before the server has answered
import { BoardState, Game, Move, Piece } from '../models';

/**
 * The board after `move`: the piece goes to its square, and the special moves do what they do (the rook with a
 * castling king, the pawn taken en passant, the promoted piece). It does not check the move is legal: the player
 * could only pick it from the game's own list (legal-moves.utils.ts), and the server checks again.
 */
export function boardAfter(board: BoardState, move: Pick<Move, 'from' | 'to' | 'piece' | 'promotion'>): BoardState {
  const squares = board.squares.map((row) => row.slice());
  const { from, to } = move;
  const piece: Piece | null = squares[from.row][from.col] ?? move.piece;
  if (!piece) return board;

  // en passant: a pawn moving diagonally onto an empty square takes the pawn beside it
  if (piece.type === 'pawn' && from.col !== to.col && !squares[to.row][to.col]) squares[from.row][to.col] = null;

  // castling: the king moves two squares, the rook jumps over it
  if (piece.type === 'king' && Math.abs(to.col - from.col) === 2) {
    const kingside = to.col > from.col;
    const rookFrom = kingside ? 7 : 0;
    const rookTo = kingside ? to.col - 1 : to.col + 1;
    squares[from.row][rookTo] = squares[from.row][rookFrom];
    squares[from.row][rookFrom] = null;
  }

  const lastRank = to.row === 0 || to.row === 7;
  squares[to.row][to.col] = piece.type === 'pawn' && lastRank
    ? { type: move.promotion ?? 'queen', color: piece.color }
    : piece;
  squares[from.row][from.col] = null;
  return { ...board, squares };
}

/**
 * The game as it will be once the server has the move: the new position, the other side to move, and no legal
 * moves until the server lists them (nobody can move meanwhile). The move list and the clocks wait for the server.
 */
export function gameAfter(game: Game, move: Pick<Move, 'from' | 'to' | 'piece' | 'promotion'>): Game {
  if (!game.board) return game;
  return {
    ...game,
    board: boardAfter(game.board, move),
    currentTurn: game.currentTurn === 'white' ? 'black' : 'white',
    legalMoves: [],
  };
}
