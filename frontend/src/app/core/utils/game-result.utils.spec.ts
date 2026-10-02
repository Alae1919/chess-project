import { Game } from '../models';
import { makeGame } from '../../testing/game-fixtures';
import { describeReason, playerColorOf, summarizeResult } from './game-result.utils';

function finished(result: Game['result'], overrides: Partial<Game> = {}): Game {
  return makeGame({ status: 'checkmate', result, ...overrides });
}

describe('playerColorOf', () => {
  const online = (white: string, black: string) => makeGame({
    mode: 'online',
    playerWhite: { ...makeGame().playerWhite, userId: white },
    playerBlack: { ...makeGame().playerBlack, userId: black },
  });

  it('finds the seat of an online player', () => {
    expect(playerColorOf(online('a', 'b'), 'a')).toBe('white');
    expect(playerColorOf(online('a', 'b'), 'b')).toBe('black');
  });

  it('is unknown for someone who is not playing, or before the profile has loaded', () => {
    expect(playerColorOf(online('a', 'b'), 'c')).toBeNull();
    expect(playerColorOf(online('a', 'b'), undefined)).toBeNull();
  });

  it('is the human side against the AI', () => {
    const vsAi = makeGame({ mode: 'ai', playerBlack: { ...makeGame().playerBlack, isAi: true } });
    expect(playerColorOf(vsAi)).toBe('white');
  });

  it('is none in a local game', () => {
    expect(playerColorOf(makeGame({ mode: 'local' }), 'a')).toBeNull();
  });
});

describe('summarizeResult', () => {
  it('is null while the game is on', () => {
    expect(summarizeResult(makeGame(), 'white')).toBeNull();
  });

  it('reads a win and a loss from the viewer\'s side', () => {
    const game = finished({ winner: 'white', reason: 'checkmate' });

    expect(summarizeResult(game, 'white')).toEqual(
      { headline: 'Victoire', reason: 'Échec et mat', tone: 'win', eloChange: null });
    expect(summarizeResult(game, 'black')?.headline).toBe('Défaite');
    expect(summarizeResult(game, 'black')?.tone).toBe('loss');
  });

  it('names the winning side when the viewer is neither', () => {
    expect(summarizeResult(finished({ winner: 'black', reason: 'timeout' }), null))
      .toEqual({ headline: 'Victoire des Noirs', reason: 'Temps écoulé', tone: 'neutral', eloChange: null });
  });

  it('reports a draw with its reason', () => {
    const summary = summarizeResult(finished({ reason: 'threefold_repetition' }), 'white');

    expect(summary?.headline).toBe('Match nul');
    expect(summary?.reason).toBe('Triple répétition');
    expect(summary?.tone).toBe('draw');
  });

  it('gives the viewer\'s own rating change', () => {
    const game = finished({ winner: 'white', reason: 'resignation', whiteEloChange: 16, blackEloChange: -16 });

    expect(summarizeResult(game, 'white')?.eloChange).toBe(16);
    expect(summarizeResult(game, 'black')?.eloChange).toBe(-16);
    expect(summarizeResult(game, null)?.eloChange).toBeNull();
  });
});

describe('describeReason', () => {
  it('words every ending the backend can report', () => {
    for (const reason of ['checkmate', 'stalemate', 'resignation', 'timeout', 'draw_agreement',
      'threefold_repetition', 'fifty_move_rule', 'insufficient_material', 'abandonment']) {
      expect(describeReason(reason)).not.toBe(reason);
    }
  });

  it('accepts the hyphenated spelling too, and passes unknown reasons through', () => {
    expect(describeReason('fifty-move-rule')).toBe('Règle des 50 coups');
    expect(describeReason('something_new')).toBe('something_new');
    expect(describeReason(undefined)).toBe('');
  });
});
