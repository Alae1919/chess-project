import { Square } from '../models';
import { DropContext, dropVerdict } from './drag-drop.utils';

const sq = (row: number, col: number): Square => ({ row, col });

describe('dropVerdict', () => {
  const ctx = (extra: Partial<DropContext> = {}): DropContext => ({
    game: { status: 'active' },
    movable: 'white',
    selected: sq(6, 4),
    legalMoves: [sq(5, 4), sq(4, 4)],
    legalMovesReady: true,
    ...extra,
  });

  it('accepts a legal square', () => {
    expect(dropVerdict(ctx(), sq(6, 4), sq(4, 4))).toBe('accept');
  });

  it('sends the piece back from a square it cannot go to', () => {
    expect(dropVerdict(ctx(), sq(6, 4), sq(3, 4))).toBe('reject');
  });

  it('waits when the legal moves of the piece have not arrived', () => {
    expect(dropVerdict(ctx({ legalMovesReady: false, legalMoves: [] }), sq(6, 4), sq(4, 4))).toBe('wait');
  });

  it('does not wait for a piece that is no longer the selected one', () => {
    expect(dropVerdict(ctx({ selected: sq(6, 3) }), sq(6, 4), sq(4, 4))).toBe('reject');
    expect(dropVerdict(ctx({ selected: null }), sq(6, 4), sq(4, 4))).toBe('reject');
  });

  it('rejects a drop on the square the piece came from', () => {
    expect(dropVerdict(ctx(), sq(6, 4), sq(6, 4))).toBe('reject');
  });

  it('rejects everything when it is not the player\'s move or the game is over', () => {
    expect(dropVerdict(ctx({ movable: null }), sq(6, 4), sq(4, 4))).toBe('reject');
    expect(dropVerdict(ctx({ game: { status: 'checkmate' } }), sq(6, 4), sq(4, 4))).toBe('reject');
    expect(dropVerdict(ctx({ game: null }), sq(6, 4), sq(4, 4))).toBe('reject');
  });
});
