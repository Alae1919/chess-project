import { legalTargets } from './legal-moves.utils';

describe('legalTargets', () => {
  const e2 = { row: 6, col: 4 };

  it('lists where the piece on a square can go', () => {
    expect(legalTargets(['e2e3', 'e2e4', 'g1f3'], e2)).toEqual([{ row: 5, col: 4 }, { row: 4, col: 4 }]);
  });

  it('counts a promotion square once', () => {
    const e7 = { row: 1, col: 4 };
    expect(legalTargets(['e7e8q', 'e7e8r', 'e7e8b', 'e7e8n'], e7)).toEqual([{ row: 0, col: 4 }]);
  });

  it('gives nothing for a piece that cannot move, and null for a game without a list', () => {
    expect(legalTargets(['g1f3'], e2)).toEqual([]);
    expect(legalTargets(undefined, e2)).toBeNull();
  });
});
