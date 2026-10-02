import { Move } from '../../core/models';
import { makeChatMessage, makeGame } from '../../testing/game-fixtures';
import { GameActions } from './game.actions';
import { gameReducer } from './game.reducer';
import { GameState, initialGameState } from './game.state';

// Only the number of moves matters to the clock
const FIRST_MOVE = {} as Move;

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

  it('stops the AI spinner when the AI move fails', () => {
    const state = withGame({ isAiThinking: true });

    const next = gameReducer(state, GameActions.aIMoveFailure({ error: 'timeout' }));

    expect(next.isAiThinking).toBeFalse();
    expect(next.error).toBe('timeout');
  });

  it('ignores a chat message it already has (HTTP response + WebSocket echo)', () => {
    const message = makeChatMessage();
    const state = withGame({ chatMessages: [message] });

    const next = gameReducer(state, GameActions.receiveChatMessage({ message: { ...message } }));

    expect(next.chatMessages.length).toBe(1);
  });

  describe('draw offers and notices', () => {
    it('takes a game update (a pending offer) into the current game', () => {
      const offered = makeGame({ drawOfferedBy: 'white' });

      const next = gameReducer(withGame(), GameActions.gameUpdated({ game: offered }));

      expect(next.currentGame).toBe(offered);
    });

    it('shows a failed request as a notice until it is dismissed', () => {
      const failed = gameReducer(withGame(), GameActions.requestFailed({ error: 'The AI declined the draw offer.' }));
      expect(failed.notice).toBe('The AI declined the draw offer.');

      expect(gameReducer(failed, GameActions.dismissNotice()).notice).toBeNull();
    });

    it('clears the notice when the next move is played', () => {
      const state = withGame({ notice: 'old' });
      const move = { from: { row: 6, col: 4 }, to: { row: 4, col: 4 }, piece: { type: 'pawn' as const, color: 'white' as const } };

      expect(gameReducer(state, GameActions.submitMove({ move })).notice).toBeNull();
    });
  });

  describe('promotion', () => {
    const pawnToLastRank = {
      from: { row: 1, col: 4 }, to: { row: 0, col: 4 },
      piece: { type: 'pawn' as const, color: 'white' as const },
    };

    it('holds a pawn move onto the last rank until a piece is chosen', () => {
      const next = gameReducer(withGame(), GameActions.submitMove({ move: pawnToLastRank }));

      expect(next.pendingPromotion).toEqual(pawnToLastRank);
      expect(next.isLoading).toBeFalse();
    });

    it('sends the move once it names its piece', () => {
      const state = withGame({ pendingPromotion: pawnToLastRank });

      const next = gameReducer(state, GameActions.submitMove({ move: { ...pawnToLastRank, promotion: 'knight' } }));

      expect(next.pendingPromotion).toBeNull();
      expect(next.isLoading).toBeTrue();
    });

    it('drops the move and the selection when cancelled', () => {
      const state = withGame({ pendingPromotion: pawnToLastRank, selectedSquare: { row: 1, col: 4 } });

      const next = gameReducer(state, GameActions.cancelPromotion());

      expect(next.pendingPromotion).toBeNull();
      expect(next.selectedSquare).toBeNull();
    });
  });

  describe('tickTimer', () => {
    it('only runs the clock of the side to move', () => {
      const state = withGame({ currentGame: makeGame({ currentTurn: 'black', moves: [FIRST_MOVE] }) });

      const next = gameReducer(state, GameActions.tickTimer());

      expect(next.currentGame!.playerWhite.timeRemainingMs).toBe(300_000);
      expect(next.currentGame!.playerBlack.timeRemainingMs).toBe(299_000);
    });

    it('never goes below zero', () => {
      const game = makeGame({ moves: [FIRST_MOVE] });
      const state = withGame({ currentGame: { ...game, playerWhite: { ...game.playerWhite, timeRemainingMs: 400 } } });

      const next = gameReducer(state, GameActions.tickTimer());

      expect(next.currentGame!.playerWhite.timeRemainingMs).toBe(0);
    });

    it('does nothing before the first move: the clocks have not started', () => {
      const state = withGame({ currentGame: makeGame() });

      expect(gameReducer(state, GameActions.tickTimer())).toBe(state);
    });

    it('does nothing in an unlimited game', () => {
      const game = makeGame({ moves: [FIRST_MOVE], timeControl: { type: 'unlimited', initialMs: 0, incrementMs: 0 } });
      const state = withGame({ currentGame: game });

      expect(gameReducer(state, GameActions.tickTimer())).toBe(state);
    });

    it('does nothing once the game is over', () => {
      const state = withGame({ currentGame: makeGame({ status: 'checkmate' }) });

      const next = gameReducer(state, GameActions.tickTimer());

      expect(next).toBe(state);
    });
  });
});
