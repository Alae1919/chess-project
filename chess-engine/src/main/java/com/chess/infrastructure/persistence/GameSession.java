package com.chess.infrastructure.persistence;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;
import com.chess.domain.rules.GameStateChecker;
import com.chess.domain.rules.SanFormatter;
import com.chess.engine.player.AiPlayer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Mutable session object held in the GameStore.
 *
 * Design notes:
 *   • Board is immutable — each move produces a new Board stored here.
 *   • moveHistory stores UCI strings (not Move objects) for cheap serialisation.
 *   • aiColor = null means human vs human ("NONE" in the API).
 *   • This class is infrastructure, not domain. If you add JPA later,
 *     annotate this class with @Entity without touching the domain.
 */
public final class GameSession {

    private final String id;
    private final Instant createdAt;
    private Board board;
    private final List<String>  moveHistory;
    private final List<String>  sanHistory;
    private final Deque<Board>  boardHistory;
    private final Color    aiColor;   // null = no AI
    private final int      aiDepth;
    private final AiPlayer aiPlayer;  // null for human-vs-human; holds TT across moves
    private GameStateChecker.State state;
    private boolean closed;              // ended before it was loaded (see restoreOutcome)
    private GameMetadata metadata;       // set by GamePersistenceService after DB persist
    private long   whiteTimeRemainingMs; // mutable — decremented on each move
    private long   blackTimeRemainingMs;
    private Instant turnStartAt;         // when the current player's clock started

    public GameSession(String id, Board initialBoard,
                       Color aiColor, int aiDepth) {
        this.id           = id;
        this.board        = initialBoard;
        this.aiColor      = aiColor;
        this.aiDepth      = aiDepth;
        this.aiPlayer     = (aiColor != null) ? new AiPlayer(aiColor, aiDepth) : null;
        this.moveHistory  = new ArrayList<>();
        this.sanHistory   = new ArrayList<>();
        this.boardHistory = new ArrayDeque<>();
        this.createdAt    = Instant.now();
        this.state        = GameStateChecker.evaluate(board, board.activeColor());
    }

    // ---- Mutation (called only from GameApplicationService) -----------

    /**
     * Initialises the chess clock.  Must be called once after the session is
     * created (by GamePersistenceService after persisting the row).
     * For unlimited time controls pass 0 for both values — the clock is not started.
     */
    public void initClock(long whiteMs, long blackMs) {
        this.whiteTimeRemainingMs = whiteMs;
        this.blackTimeRemainingMs = blackMs;
        if (whiteMs > 0 || blackMs > 0) {
            this.turnStartAt = Instant.now();
        }
    }

    public void applyMove(com.chess.domain.model.Move move) {
        if (isOver())
            throw new IllegalStateException("Cannot apply move: game is over");

        // Decrement the active player's clock (skip for unlimited / uninitialised)
        if (turnStartAt != null) {
            long elapsed = Duration.between(turnStartAt, Instant.now()).toMillis();
            if (board.activeColor() == Color.WHITE) {
                whiteTimeRemainingMs = Math.max(0, whiteTimeRemainingMs - elapsed);
            } else {
                blackTimeRemainingMs = Math.max(0, blackTimeRemainingMs - elapsed);
            }
        }

        boardHistory.push(board);
        moveHistory.add(move.toUci());
        Board before = board;
        board = board.apply(move);
        state = GameStateChecker.evaluate(board, board.activeColor());
        sanHistory.add(SanFormatter.format(before, move, state));

        if (turnStartAt != null) {
            turnStartAt = Instant.now(); // start the next player's clock
        }
    }

    /** Reverts the last move. Throws if no move has been played yet. */
    public void undoLastMove() {
        if (boardHistory.isEmpty())
            throw new IllegalStateException("No moves to undo");
        board = boardHistory.pop();
        if (!moveHistory.isEmpty())
            moveHistory.remove(moveHistory.size() - 1);
        if (!sanHistory.isEmpty())
            sanHistory.remove(sanHistory.size() - 1);
        state = GameStateChecker.evaluate(board, board.activeColor());
    }

    /** {@code loser} resigns; the opponent wins. */
    public void resign(Color loser) {
        if (isOver()) return;
        state = loser == Color.WHITE
            ? GameStateChecker.State.WHITE_RESIGNED
            : GameStateChecker.State.BLACK_RESIGNED;
    }

    /**
     * Marks a game loaded from the database as already over. {@code outcome} is
     * how it ended, or null when the engine has no state for that ending (an
     * aborted game); either way no further moves are accepted.
     */
    public void restoreOutcome(GameStateChecker.State outcome) {
        if (outcome != null) state = outcome;
        closed = true;
    }

    /** Ends the game as a draw by agreement. */
    public void agreeDraw() {
        if (isOver()) return;
        state = GameStateChecker.State.DRAW_AGREED;
    }

    // ---- Read-only access --------------------------------------------

    public String                    id()          { return id; }
    public Board                     board()       { return board; }
    public Color                     aiColor()     { return aiColor; }
    public int                       aiDepth()     { return aiDepth; }
    public AiPlayer                  aiPlayer()    { return aiPlayer; }
    public GameStateChecker.State    state()       { return state; }
    public List<String>              moveHistory() { return Collections.unmodifiableList(moveHistory); }
    public List<String>              sanHistory()  { return Collections.unmodifiableList(sanHistory); }
    public Instant                   createdAt()   { return createdAt; }
    public boolean                   isOver()      { return closed || GameStateChecker.isTerminal(state); }
    public GameMetadata              metadata()              { return metadata; }
    public void                      setMetadata(GameMetadata m) { this.metadata = m; }
    public long                      whiteTimeRemainingMs()  { return whiteTimeRemainingMs; }
    public long                      blackTimeRemainingMs()  { return blackTimeRemainingMs; }

    public String lastMove() {
        return moveHistory.isEmpty() ? null : moveHistory.get(moveHistory.size() - 1);
    }
}
