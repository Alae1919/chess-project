package com.chess.infrastructure.persistence;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;
import com.chess.domain.rules.GameStateChecker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
    private final List<String> moveHistory;
    private final Color aiColor;   // null = no AI
    private final int   aiDepth;
    private GameStateChecker.State state;

    public GameSession(String id, Board initialBoard,
                       Color aiColor, int aiDepth) {
        this.id          = id;
        this.board       = initialBoard;
        this.aiColor     = aiColor;
        this.aiDepth     = aiDepth;
        this.moveHistory = new ArrayList<>();
        this.createdAt   = Instant.now();
        this.state       = GameStateChecker.evaluate(board, board.activeColor());
    }

    // ---- Mutation (called only from GameApplicationService) -----------

    public void applyMove(com.chess.domain.model.Move move) {
        if (isOver())
            throw new IllegalStateException("Cannot apply move: game is over");
        moveHistory.add(move.toString());
        board = board.apply(move);
        state = GameStateChecker.evaluate(board, board.activeColor());
    }

    /** The player to move resigns; opponent wins. */
    public void resignAsActivePlayer() {
        if (isOver()) return;
        Color active = board.activeColor();
        state = active == Color.WHITE
            ? GameStateChecker.State.WHITE_RESIGNED
            : GameStateChecker.State.BLACK_RESIGNED;
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
    public GameStateChecker.State    state()       { return state; }
    public List<String>              moveHistory() { return Collections.unmodifiableList(moveHistory); }
    public Instant                   createdAt()   { return createdAt; }
    public boolean                   isOver()      { return GameStateChecker.isTerminal(state); }

    public String lastMove() {
        return moveHistory.isEmpty() ? null : moveHistory.get(moveHistory.size() - 1);
    }
}
