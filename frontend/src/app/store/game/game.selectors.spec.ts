import { Game, User } from '../../core/models';
import { makeGame } from '../../testing/game-fixtures';
import { initialGameState, GameState } from './game.state';
import { selectMovableColor } from './game.selectors';

describe('selectMovableColor', () => {
  const ai = (color: 'white' | 'black') =>
    ({ username: 'AI', color, timeRemainingMs: 0, capturedPieces: [], isAi: true });
  const human = (color: 'white' | 'black', userId: string) =>
    ({ username: color, color, timeRemainingMs: 0, capturedPieces: [], userId });

  const user = { id: 'me' } as User;

  function movable(game: Game | null, extra: Partial<GameState> = {}, who: User | null = user) {
    const state = { game: { ...initialGameState, currentGame: game, ...extra }, account: { user: who } } as any;
    return selectMovableColor(state);
  }

  it('is nothing when there is no game', () => {
    expect(movable(null)).toBeNull();
  });

  it('is the side to move in a local game, whichever it is', () => {
    expect(movable(makeGame({ mode: 'local', currentTurn: 'white' }))).toBe('white');
    expect(movable(makeGame({ mode: 'local', currentTurn: 'black' }))).toBe('black');
  });

  it('against the AI, only the human\'s pieces can be moved, and only on their turn', () => {
    const asWhite = (turn: 'white' | 'black') =>
      makeGame({ mode: 'ai', playerWhite: human('white', 'me'), playerBlack: ai('black'), currentTurn: turn });

    expect(movable(asWhite('white'))).toBe('white');
    expect(movable(asWhite('black'))).toBeNull();            // the AI's turn: its pieces are off limits
  });

  it('a player of Black cannot pick up White\'s pieces in an online game', () => {
    const online = (turn: 'white' | 'black') =>
      makeGame({ mode: 'online', playerWhite: human('white', 'them'), playerBlack: human('black', 'me'), currentTurn: turn });

    expect(movable(online('white'))).toBeNull();
    expect(movable(online('black'))).toBe('black');
  });

  it('an online game with the profile not loaded yet can\'t be moved in', () => {
    const online = makeGame({ mode: 'online', playerWhite: human('white', 'them'), playerBlack: human('black', 'me') });

    expect(movable(online, {}, null)).toBeNull();
  });

  it('is nothing while a move is on its way to the server', () => {
    const game = makeGame({ mode: 'local', currentTurn: 'white' });

    expect(movable(game, { isLoading: true })).toBeNull();
  });

  it('is nothing while the AI is thinking', () => {
    const game = makeGame({ mode: 'ai', playerWhite: human('white', 'me'), playerBlack: ai('black'), currentTurn: 'white' });

    expect(movable(game, { isAiThinking: true })).toBeNull();
  });
});
