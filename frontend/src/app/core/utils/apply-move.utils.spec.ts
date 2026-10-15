import { BoardState, Piece } from '../models';
import { boardAfter } from './apply-move.utils';

const P = (type: Piece['type'], color: Piece['color']): Piece => ({ type, color });
const empty = (): BoardState => ({ squares: Array.from({ length: 8 }, () => Array<Piece | null>(8).fill(null)) });
const at = (b: BoardState, alg: string) => b.squares[8 - Number(alg[1])][alg.charCodeAt(0) - 97];
const sq = (alg: string) => ({ row: 8 - Number(alg[1]), col: alg.charCodeAt(0) - 97 });
function put(b: BoardState, alg: string, p: Piece): BoardState {
  b.squares[8 - Number(alg[1])][alg.charCodeAt(0) - 97] = p;
  return b;
}

describe('boardAfter', () => {
  it('moves the piece, taking what stood there, and leaves the board it was given alone', () => {
    const before = put(put(empty(), 'e4', P('pawn', 'white')), 'd5', P('pawn', 'black'));
    const after = boardAfter(before, { from: sq('e4'), to: sq('d5'), piece: P('pawn', 'white') });
    expect(at(after, 'd5')).toEqual(P('pawn', 'white'));
    expect(at(after, 'e4')).toBeNull();
    expect(at(before, 'e4')).toEqual(P('pawn', 'white'));
  });

  it('takes the pawn beside on an en passant capture', () => {
    const before = put(put(empty(), 'e5', P('pawn', 'white')), 'd5', P('pawn', 'black'));
    const after = boardAfter(before, { from: sq('e5'), to: sq('d6'), piece: P('pawn', 'white') });
    expect(at(after, 'd6')).toEqual(P('pawn', 'white'));
    expect(at(after, 'd5')).toBeNull();
  });

  it('brings the rook over the king when castling, either side', () => {
    const start = () => put(put(put(empty(), 'e1', P('king', 'white')), 'h1', P('rook', 'white')), 'a1', P('rook', 'white'));
    const short = boardAfter(start(), { from: sq('e1'), to: sq('g1'), piece: P('king', 'white') });
    expect(at(short, 'g1')?.type).toBe('king');
    expect(at(short, 'f1')?.type).toBe('rook');
    expect(at(short, 'h1')).toBeNull();
    const long = boardAfter(start(), { from: sq('e1'), to: sq('c1'), piece: P('king', 'white') });
    expect(at(long, 'c1')?.type).toBe('king');
    expect(at(long, 'd1')?.type).toBe('rook');
    expect(at(long, 'a1')).toBeNull();
  });

  it('promotes to the piece chosen', () => {
    const before = put(empty(), 'b7', P('pawn', 'white'));
    const after = boardAfter(before, { from: sq('b7'), to: sq('b8'), piece: P('pawn', 'white'), promotion: 'knight' });
    expect(at(after, 'b8')).toEqual(P('knight', 'white'));
  });
});
