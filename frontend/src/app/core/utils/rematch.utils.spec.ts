import { makeGame } from '../../testing/game-fixtures';
import { rematchInvitation, rematchOptions } from './rematch.utils';

describe('rematch', () => {
  it('invites the same opponent on the same clock with colours swapped', () => {
    const game = makeGame({
      mode: 'online',
      playerWhite: { ...makeGame().playerWhite, username: 'ann' },
      playerBlack: { ...makeGame().playerBlack, username: 'bob' },
      timeControl: { type: 'blitz', initialMs: 180_000, incrementMs: 2_000 },
    });

    expect(rematchInvitation(game, 'white')).toEqual({
      inviteeUsername: 'bob', timeControlType: 'blitz',
      timeControlInitialMs: 180_000, timeControlIncrementMs: 2_000, inviterColor: 'black',
    });
    expect(rematchInvitation(game, 'black').inviteeUsername).toBe('ann');
    expect(rematchInvitation(game, 'black').inviterColor).toBe('white');
  });

  it('starts the same AI game again from the other side', () => {
    const base = makeGame();
    const game = makeGame({ mode: 'ai', playerBlack: { ...base.playerBlack, isAi: true, aiDifficulty: 5 } });

    const options = rematchOptions(game, 'white');

    expect(options.mode).toBe('ai');
    expect(options.aiDifficulty).toBe(5);
    expect(options.playerColor).toBe('black');
    expect(options.timeControl).toEqual(game.timeControl);
  });

  it('starts a local game again as it was', () => {
    expect(rematchOptions(makeGame({ mode: 'local' }), null).playerColor).toBe('white');
  });
});
