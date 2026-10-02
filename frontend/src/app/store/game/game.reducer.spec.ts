import { makeChatMessage, makeGame } from '../../testing/game-fixtures';
import { GameActions } from './game.actions';
import { gameReducer } from './game.reducer';
import { GameState, initialGameState } from './game.state';

describe('gameReducer', () => {
  const withGame = (overrides: Partial<GameState> = {}): GameState => ({
    ...initialGameState,
    currentGame: makeGame(),
    ...overrides,
  });

  describe('loadGame', () => {
    it('keeps chat and selection when reloading the same game', () => {
      const state = withGame({ chatMessages: [makeChatMessage()], selectedSquare: { row: 6, col: 4 } });

      const next = gameReducer(state, GameActions.loadGame({ gameId: 'game-1' }));

      expect(next.chatMessages.length).toBe(1);
      expect(next.selectedSquare).toEqual({ row: 6, col: 4 });
      expect(next.isLoading).toBeTrue();
    });

    it('drops the previous game state when switching to another game', () => {
      const state = withGame({ chatMessages: [makeChatMessage()], selectedSquare: { row: 6, col: 4 } });

      const next = gameReducer(state, GameActions.loadGame({ gameId: 'game-2' }));

      expect(next.currentGame).toBeNull();
      expect(next.chatMessages).toEqual([]);
      expect(next.selectedSquare).toBeNull();
    });
  });

  it('clears the selection and legal moves after a move is applied', () => {
    const state = withGame({ selectedSquare: { row: 6, col: 4 }, legalMoves: [{ row: 4, col: 4 }], isLoading: true });
    const moved = makeGame({ currentTurn: 'black' });

    const next = gameReducer(state, GameActions.submitMoveSuccess({ game: moved }));

    expect(next.currentGame).toBe(moved);
    expect(next.selectedSquare).toBeNull();
    expect(next.legalMoves).toEqual([]);
    expect(next.isLoading).toBeFalse();
  });

  it('stops the AI spinner when the AI move arrives', () => {
    const state = withGame({ isAiThinking: true });

    const next = gameReducer(state, GameActions.aIMoveSuccess({ game: makeGame() }));

    expect(next.isAiThinking).toBeFalse();
  });

  it('ignores a chat message it already has (HTTP response + WebSocket echo)', () => {
    const message = makeChatMessage();
    const state = withGame({ chatMessages: [message] });

    const next = gameReducer(state, GameActions.receiveChatMessage({ message: { ...message } }));

    expect(next.chatMessages.length).toBe(1);
  });

  describe('tickTimer', () => {
    it('only runs the clock of the side to move', () => {
      const state = withGame({ currentGame: makeGame({ currentTurn: 'black' }) });

      const next = gameReducer(state, GameActions.tickTimer());

      expect(next.currentGame!.playerWhite.timeRemainingMs).toBe(300_000);
      expect(next.currentGame!.playerBlack.timeRemainingMs).toBe(299_000);
    });

    it('never goes below zero', () => {
      const game = makeGame();
      const state = withGame({ currentGame: { ...game, playerWhite: { ...game.playerWhite, timeRemainingMs: 400 } } });

      const next = gameReducer(state, GameActions.tickTimer());

      expect(next.currentGame!.playerWhite.timeRemainingMs).toBe(0);
    });

    it('does nothing once the game is over', () => {
      const state = withGame({ currentGame: makeGame({ status: 'checkmate' }) });

      const next = gameReducer(state, GameActions.tickTimer());

      expect(next).toBe(state);
    });
  });
});
