package com.chess.infrastructure.persistence;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;
import com.chess.domain.rules.GameStateChecker;
import com.chess.domain.rules.InsufficientMaterial;
import com.chess.domain.rules.SanFormatter;
import com.chess.engine.player.AiPlayer;
import com.chess.infrastructure.api.FenSerializer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final Map<String, Integer> positionCounts = new HashMap<>(); // for threefold repetition
    private final Color    aiColor;   // null = no AI
    private final int      aiDepth;
    private final AiPlayer aiPlayer;  // null for human-vs-human; holds TT across moves
    private GameStateChecker.State state;
    private boolean closed;              // ended before it was loaded (see restoreOutcome)
    private GameMetadata metadata;       // set by GamePersistenceService after DB persist
    private final Clock clock;           // injectable so tests can move time
    private boolean timed;               // false for unlimited games: no clock at all
    private long   whiteTimeRemainingMs; // as of turnStartAt; read through the *TimeRemainingMs() accessors
    private long   blackTimeRemainingMs;
    private long   incrementMs;          // added to the mover's clock after each move
    private Instant turnStartAt;         // when the side to move's clock started; null = not running

    public GameSession(String id, Board initialBoard,
                       Color aiColor, int aiDepth) {
        this(id, initialBoard, aiColor, aiDepth, Clock.systemUTC());
    }

    public GameSession(String id, Board initialBoard,
                       Color aiColor, int aiDepth, Clock clock) {
        this.clock        = clock;
        this.id           = id;
        this.board        = initialBoard;
        this.aiColor      = aiColor;
        this.aiDepth      = aiDepth;
        this.aiPlayer     = (aiColor != null) ? new AiPlayer(aiColor, aiDepth) : null;
        this.moveHistory  = new ArrayList<>();
        this.sanHistory   = new ArrayList<>();
        this.boardHistory = new ArrayDeque<>();
        this.createdAt    = clock.instant();
        this.state        = GameStateChecker.evaluate(board, board.activeColor());
        countPosition(board);
    }

    // ---- Mutation (called only from GameApplicationService) -----------

    /**
     * Sets up the chess clock; called once after the session is created or restored.
     * Pass 0 for both times for an unlimited game: it has no clock. The clock runs
     * once White has made the first move, so a game with no moves yet isn't ticking.
     */
    public synchronized void initClock(long whiteMs, long blackMs, long incrementMs) {
        this.whiteTimeRemainingMs = whiteMs;
        this.blackTimeRemainingMs = blackMs;
        this.incrementMs          = incrementMs;
        this.timed                = whiteMs > 0 || blackMs > 0;
        this.turnStartAt          = timed && !moveHistory.isEmpty() && !isOver() ? clock.instant() : null;
    }

    public synchronized void applyMove(com.chess.domain.model.Move move) {
        if (isOver())
            throw new IllegalStateException("Cannot apply move: game is over");

        // The mover's clock: charge the time used (not for White's first move, when
        // nothing is running yet), then add the increment
        if (timed) {
            boolean white = board.activeColor() == Color.WHITE;
            long left = remaining(board.activeColor());
            left = Math.max(0, left) + (left > 0 ? incrementMs : 0);
            if (white) whiteTimeRemainingMs = left; else blackTimeRemainingMs = left;
        }

        boardHistory.push(board);
        moveHistory.add(move.toUci());
        Board before = board;
        board = board.apply(move);
        state = GameStateChecker.evaluate(board, board.activeColor());
        // Mate and the other draws outrank a third repetition
        if (countPosition(board) >= 3
                && (state == GameStateChecker.State.ONGOING || state == GameStateChecker.State.CHECK))
            state = GameStateChecker.State.DRAW_REPETITION;
        sanHistory.add(SanFormatter.format(before, move, state));

        // The opponent's clock starts now, unless the move ended the game
        turnStartAt = timed && !isOver() ? clock.instant() : null;
    }

    /** Reverts the last move. Throws if no move has been played yet. */
    public synchronized void undoLastMove() {
        if (boardHistory.isEmpty())
            throw new IllegalStateException("No moves to undo");
        uncountPosition(board);
        board = boardHistory.pop();
        if (!moveHistory.isEmpty())
            moveHistory.remove(moveHistory.size() - 1);
        if (!sanHistory.isEmpty())
            sanHistory.remove(sanHistory.size() - 1);
        state = GameStateChecker.evaluate(board, board.activeColor());
        turnStartAt = timed && !moveHistory.isEmpty() ? clock.instant() : null;
    }

    /** {@code loser} resigns; the opponent wins. */
    public synchronized void resign(Color loser) {
        if (isOver()) return;
        stopClock(); // before the state changes: a finished game's clock reads as frozen
        state = loser == Color.WHITE
            ? GameStateChecker.State.WHITE_RESIGNED
            : GameStateChecker.State.BLACK_RESIGNED;
    }

    /**
     * The side whose time has run out, or null. Only the side to move can: its
     * clock is the one running.
     */
    public synchronized Color flaggedSide() {
        if (!timed || isOver() || turnStartAt == null) return null;
        return remaining(board.activeColor()) <= 0 ? board.activeColor() : null;
    }

    /**
     * {@code loser} ran out of time. The opponent wins, unless it could never have
     * mated anyway (a bare king or one minor piece), which is a draw.
     */
    public synchronized void flag(Color loser) {
        if (isOver()) return;
        if (loser == Color.WHITE) whiteTimeRemainingMs = 0; else blackTimeRemainingMs = 0;
        turnStartAt = null;
        if (InsufficientMaterial.cannotMate(board, loser.opposite())) {
            state = GameStateChecker.State.DRAW_INSUFFICIENT_MATERIAL;
        } else {
            state = loser == Color.WHITE
                ? GameStateChecker.State.WHITE_FLAGGED
                : GameStateChecker.State.BLACK_FLAGGED;
        }
    }

    /** Freezes the clocks, charging the side to move for the time it has used so far. */
    private void stopClock() {
        if (turnStartAt == null) return;
        long left = remaining(board.activeColor());
        if (board.activeColor() == Color.WHITE) whiteTimeRemainingMs = left; else blackTimeRemainingMs = left;
        turnStartAt = null;
    }

    /** Time left for {@code side} right now: the stored value minus the running turn, never negative. */
    private long remaining(Color side) {
        long stored = side == Color.WHITE ? whiteTimeRemainingMs : blackTimeRemainingMs;
        if (turnStartAt == null || side != board.activeColor() || isOver()) return stored;
        return Math.max(0, stored - Duration.between(turnStartAt, clock.instant()).toMillis());
    }

    /**
     * Counts the position and returns how often it has now occurred. Two positions
     * are the same when pieces, side to move, castling rights and en-passant target
     * match (the first four FEN fields); the move counters don't matter.
     */
    private int countPosition(Board b) {
        return positionCounts.merge(positionKey(b), 1, Integer::sum);
    }

    private void uncountPosition(Board b) {
        positionCounts.computeIfPresent(positionKey(b), (k, n) -> n > 1 ? n - 1 : null);
    }

    private static String positionKey(Board b) {
        String[] fields = FenSerializer.toFen(b).split(" ");
        return String.join(" ", fields[0], fields[1], fields[2], fields[3]);
    }

    /**
     * Marks a game loaded from the database as already over. {@code outcome} is
     * how it ended, or null when the engine has no state for that ending (an
     * aborted game); either way no further moves are accepted.
     */
    public synchronized void restoreOutcome(GameStateChecker.State outcome) {
        if (outcome != null) state = outcome;
        closed = true;
        turnStartAt = null;
    }

    /** Ends the game as a draw by agreement. */
    public synchronized void agreeDraw() {
        if (isOver()) return;
        stopClock();
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
    public synchronized long         whiteTimeRemainingMs()  { return remaining(Color.WHITE); }
    public synchronized long         blackTimeRemainingMs()  { return remaining(Color.BLACK); }
    public synchronized boolean      isTimed()               { return timed; }

    public String lastMove() {
        return moveHistory.isEmpty() ? null : moveHistory.get(moveHistory.size() - 1);
    }
}
