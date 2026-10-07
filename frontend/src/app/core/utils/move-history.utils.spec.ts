import { Move, Square } from '../models';
import { applyMove, checkedKingAt, positionsOf, sameSquares, sideToMoveAt, startingSquares } from './move-history.utils';

// the server sends coordinates and notation: row 0 is rank 8, col 0 is file a
const sq = (name: string): Square => ({ col: name.charCodeAt(0) - 97, row: 8 - Number(name[1]) });
const mv = (from: string, to: string, algebraicNotation = `${from}${to}`): Move =>
  ({ from: sq(from), to: sq(to), algebraicNotation }) as Move;

const at = (squares: ReturnType<typeof startingSquares>, name: string) => squares[sq(name).row][sq(name).col];

describe('move history', () => {
  it('starts from the usual position', () => {
    const s = startingSquares();
    expect(at(s, 'e1')).toEqual({ type: 'king', color: 'white' });
    expect(at(s, 'd8')).toEqual({ type: 'queen', color: 'black' });
    expect(at(s, 'a2')).toEqual({ type: 'pawn', color: 'white' });
    expect(at(s, 'e4')).toBeNull();
  });

  it('moves a piece without touching the position it came from', () => {
    const before = startingSquares();
    const after = applyMove(before, mv('e2', 'e4'));

    expect(at(after, 'e4')).toEqual({ type: 'pawn', color: 'white' });
    expect(at(after, 'e2')).toBeNull();
    expect(at(before, 'e2')).toEqual({ type: 'pawn', color: 'white' });
  });

  it('removes the pawn taken en passant', () => {
    let s = startingSquares();
    for (const m of [mv('e2', 'e4'), mv('a7', 'a6'), mv('e4', 'e5'), mv('d7', 'd5'), mv('e5', 'd6')]) s = applyMove(s, m);

    expect(at(s, 'd6')).toEqual({ type: 'pawn', color: 'white' });
    expect(at(s, 'd5')).toBeNull();
  });

  it('moves the rook when the king castles', () => {
    let s = startingSquares();
    for (const m of [mv('e2', 'e4'), mv('e7', 'e5'), mv('g1', 'f3'), mv('b8', 'c6'), mv('f1', 'c4'), mv('g8', 'f6'), mv('e1', 'g1')]) s = applyMove(s, m);

    expect(at(s, 'g1')).toEqual({ type: 'king', color: 'white' });
    expect(at(s, 'f1')).toEqual({ type: 'rook', color: 'white' });
    expect(at(s, 'h1')).toBeNull();
  });

  it('crowns the pawn with the piece named in the notation', () => {
    let s = startingSquares();
    s[1][0] = null;                                   // a7 is empty so the pawn can run
    s[1][1] = null;
    s[6][1] = null;
    s[2][1] = { type: 'pawn', color: 'white' };       // a white pawn on b6
    s = applyMove(applyMove(s, mv('b6', 'b7')), mv('b7', 'a8', 'bxa8=N'));

    expect(at(s, 'a8')).toEqual({ type: 'knight', color: 'white' });
  });

  describe('positionsOf', () => {
    const moves = [mv('e2', 'e4'), mv('e7', 'e5'), mv('g1', 'f3')];

    it('lists the position before the first move and after each one', () => {
      const current = { squares: moves.reduce((s, m) => applyMove(s, m), startingSquares()) };
      const positions = positionsOf(moves, current)!;

      expect(positions.length).toBe(4);
      expect(sameSquares(positions[0], startingSquares())).toBeTrue();
      expect(at(positions[1], 'e4')).toEqual({ type: 'pawn', color: 'white' });
      expect(at(positions[3], 'f3')).toEqual({ type: 'knight', color: 'white' });
      expect(at(positions[2], 'f3')).toBeNull();
    });

    it('gives up when the moves do not lead to the board on screen (a game that began elsewhere)', () => {
      expect(positionsOf(moves, { squares: startingSquares() })).toBeNull();
    });

    it('has nothing without a board', () => {
      expect(positionsOf(moves, null)).toBeNull();
    });
  });

  it('knows whose turn it was', () => {
    expect(sideToMoveAt(0)).toBe('white');
    expect(sideToMoveAt(1)).toBe('black');
    expect(sideToMoveAt(2)).toBe('white');
  });

  it('finds the king that the last move put in check', () => {
    let s = startingSquares();
    for (const m of [mv('e2', 'e4'), mv('f7', 'f6'), mv('d1', 'h5')]) s = applyMove(s, m);

    expect(checkedKingAt(s, 3, { isCheck: true })).toEqual(sq('e8'));
    expect(checkedKingAt(s, 3, { isCheck: false })).toBeNull();
    expect(checkedKingAt(s, 0, undefined)).toBeNull();
  });
});
