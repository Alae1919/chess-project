import { Game, Move, User } from '../../core/models';
import { makeGame } from '../../testing/game-fixtures';
import { initialGameState, GameState } from './game.state';
import { applyMove, startingSquares } from '../../core/utils/move-history.utils';
import {
  selectAnalysis, selectCanReview, selectDisplayedBoard, selectHint, selectMovableColor, selectReviewPly, selectViewedMove, selectViewPly,
} from './game.selectors';

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

describe('analysis selectors', () => {
  it('read the analysis switch and the hint from the game state', () => {
    const state = { game: { ...initialGameState, analysis: true, hint: 'e2e4' } } as any;

    expect(selectAnalysis(state)).toBeTrue();
    expect(selectHint(state)).toBe('e2e4');
  });
});

describe('looking back selectors', () => {
  const sq = (name: string) => ({ col: name.charCodeAt(0) - 97, row: 8 - Number(name[1]) });
  const moves = [['e2', 'e4'], ['e7', 'e5'], ['g1', 'f3']].map(([f, t]) => ({ from: sq(f), to: sq(t), algebraicNotation: f + t }) as Move);
  const board = { squares: moves.reduce((s, m) => applyMove(s, m), startingSquares()) };
  const game = makeGame({ mode: 'local', moves, board, currentTurn: 'black' });

  const select = <T>(selector: (s: any) => T, extra: Partial<GameState> = {}) =>
    selector({ game: { ...initialGameState, currentGame: game, ...extra }, account: { user: null } });

  it('shows the live board while nobody looks back', () => {
    expect(select(selectReviewPly)).toBeNull();
    expect(select(selectViewPly)).toBe(3);
    expect(select(selectDisplayedBoard)).toBe(board);
    expect(select(selectViewedMove)).toBe(moves[2]);
  });

  it('shows the board as it was after the move being looked at', () => {
    const shown = select(selectDisplayedBoard, { reviewPly: 1 })!;

    expect(shown.squares[sq('e4').row][sq('e4').col]).toEqual({ type: 'pawn', color: 'white' });
    expect(shown.squares[sq('e5').row][sq('e5').col]).toBeNull();
    expect(select(selectViewPly, { reviewPly: 1 })).toBe(1);
    expect(select(selectViewedMove, { reviewPly: 1 })).toBe(moves[0]);
  });

  it('can go back as soon as a move has been played', () => {
    expect(select(selectCanReview)).toBeTrue();
  });

  it('cannot when the moves do not lead to the board shown', () => {
    const odd = makeGame({ mode: 'local', moves, board: { squares: startingSquares() } });
    const state = { game: { ...initialGameState, currentGame: odd, reviewPly: 1 }, account: { user: null } } as any;

    expect(selectCanReview(state)).toBeFalse();
    expect(selectReviewPly(state)).toBeNull();
    expect(selectDisplayedBoard(state)).toBe(odd.board);
  });

  it('lets nobody move a piece on a past position', () => {
    expect(select(selectMovableColor)).toBe('black');
    expect(select(selectMovableColor, { reviewPly: 1 })).toBeNull();
  });
});
