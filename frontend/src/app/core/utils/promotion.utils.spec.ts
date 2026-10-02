import { Piece } from '../models';
import { needsPromotionChoice } from './promotion.utils';

describe('needsPromotionChoice', () => {
  const pawn = (color: 'white' | 'black'): Piece => ({ type: 'pawn', color });

  it('is true for a pawn reaching rank 8 or rank 1 without a piece', () => {
    expect(needsPromotionChoice({ piece: pawn('white'), to: { row: 0, col: 4 } })).toBeTrue();
    expect(needsPromotionChoice({ piece: pawn('black'), to: { row: 7, col: 0 } })).toBeTrue();
  });

  it('is false once the piece is chosen', () => {
    expect(needsPromotionChoice({ piece: pawn('white'), to: { row: 0, col: 4 }, promotion: 'queen' })).toBeFalse();
  });

  it('is false for other pawn moves and for other pieces', () => {
    expect(needsPromotionChoice({ piece: pawn('white'), to: { row: 4, col: 4 } })).toBeFalse();
    expect(needsPromotionChoice({ piece: { type: 'rook', color: 'white' }, to: { row: 0, col: 0 } })).toBeFalse();
  });
});
