import { GameStatus } from '../models';
import { isPlayableStatus, isTerminalStatus } from './game-status.utils';

describe('game status helpers', () => {
  const ended: GameStatus[] = [
    'checkmate', 'stalemate', 'draw_50_move', 'draw_insufficient_material', 'draw_repetition',
    'white_resigned', 'black_resigned', 'draw_agreed', 'finished', 'aborted',
  ];

  it('treats every way a game can end as terminal and not playable', () => {
    for (const status of ended) {
      expect(isTerminalStatus(status)).withContext(status).toBeTrue();
      expect(isPlayableStatus(status)).withContext(status).toBeFalse();
    }
  });

  it('keeps active and check games playable', () => {
    for (const status of ['active', 'check'] as GameStatus[]) {
      expect(isPlayableStatus(status)).toBeTrue();
      expect(isTerminalStatus(status)).toBeFalse();
    }
  });
});
