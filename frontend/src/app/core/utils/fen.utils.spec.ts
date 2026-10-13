import { fenToSquares, moveLabel, squareOf, uciSquares } from './fen.utils';

describe('fen.utils', () => {
  it('reads the placement field into rows from rank 8 down', () => {
    const sq = fenToSquares('rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1');
    expect(sq.length).toBe(8);
    expect(sq.every((r) => r.length === 8)).toBeTrue();
    expect(sq[0][4]).toEqual({ type: 'king', color: 'black' });
    expect(sq[7][3]).toEqual({ type: 'queen', color: 'white' });
    expect(sq[4][4]).toEqual({ type: 'pawn', color: 'white' });
    expect(sq[6][4]).toBeNull();
  });

  it('refuses text that is not a position', () => {
    expect(() => fenToSquares('nonsense')).toThrow();
    expect(() => fenToSquares('rnbqkbnr/ppppxppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1')).toThrow();
    expect(() => fenToSquares('rnbqkbnr/ppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1')).toThrow();
  });

  it('turns square names and UCI moves into board squares', () => {
    expect(squareOf('a8')).toEqual({ row: 0, col: 0 });
    expect(squareOf('h1')).toEqual({ row: 7, col: 7 });
    expect(uciSquares('d6e5')).toEqual({ from: { row: 2, col: 3 }, to: { row: 3, col: 4 } });
    expect(uciSquares('e7e8q').to).toEqual({ row: 0, col: 4 });
    expect(() => squareOf('z9')).toThrow();
  });

  it('numbers a move by whose turn it is', () => {
    expect(moveLabel('8/8/8/8/8/8/8/8 w - - 0 5', 'Nxe5')).toBe('5. Nxe5');
    expect(moveLabel('8/8/8/8/8/8/8/8 b - - 0 5', 'dxe5')).toBe('5… dxe5');
  });
});
