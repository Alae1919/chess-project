package com.chess.engine.player;

import com.chess.domain.board.Board;
import com.chess.domain.board.FenParser;
import com.chess.domain.model.Color;
import com.chess.domain.model.Move;
import com.chess.domain.rules.MoveGenerator;
import com.chess.engine.core.Position;
import com.chess.engine.core.book.PolyglotBook;
import com.chess.engine.core.eval.ClassicalEvaluator;
import com.chess.engine.core.search.SearchLimits;
import com.chess.engine.core.search.SearchResult;
import com.chess.engine.core.search.Searcher;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * The AI opponent: adapts the application's {@link Board} to the engine in
 * {@code com.chess.engine.core}, applies the difficulty level, and hands back a domain move.
 *
 * Move selection: the opening book if the position is in it, otherwise a search whose
 * length depends on the level and on how much time is left on the game clock.
 *
 * The searcher (with its transposition table) is created on the first move and kept for the
 * life of the player. One request uses a player at a time; {@link #stop()} may come from
 * another thread.
 */
public final class AiPlayer implements Player {

    private static final PolyglotBook BOOK = PolyglotBook.fromResource("/openings/book.bin");
    /** The book is only consulted early in the game. */
    private static final int BOOK_MAX_FULLMOVE = 12;

    private final Color color;
    private final AiLevel level;
    private final RandomGenerator random;
    private volatile Searcher searcher;

    public AiPlayer(Color color) { this(color, AiLevel.DEFAULT); }
    public AiPlayer(Color color, int level) { this(color, level, ThreadLocalRandom.current()); }

    public AiPlayer(Color color, int level, RandomGenerator random) {
        this.color = color;
        this.level = AiLevel.of(level);
        this.random = random;
    }

    @Override
    public Color color() { return color; }

    public AiLevel level() { return level; }

    @Override
    public Move chooseMove(Board board) {
        return chooseMove(board, board, List.of(), 0, 0, 0);
    }

    /**
     * @param board       the position to move in
     * @param startBoard  where the game began, so earlier positions count when looking for repetitions
     * @param moves       the moves played from {@code startBoard} (UCI), ending at {@code board}
     * @param whiteMs     White's clock, or 0 in an untimed game
     * @param blackMs     Black's clock, or 0
     * @param incrementMs the increment per move
     */
    public Move chooseMove(Board board, Board startBoard, List<String> moves, long whiteMs, long blackMs, long incrementMs) {
        Position pos = positionWithHistory(board, startBoard, moves);
        if (pos.legalMoves().length == 0) {
            throw new IllegalStateException("chooseMove() called on a terminal position for " + color);
        }

        if (!BOOK.isEmpty() && pos.fullmoveNumber() <= BOOK_MAX_FULLMOVE) {
            int book = BOOK.pick(pos, random);
            if (book != com.chess.engine.core.Move.NONE) return toDomain(board, book);
        }

        Searcher s = searcher;
        if (s == null) searcher = s = new Searcher(new ClassicalEvaluator(), level.hashMegabytes());
        long remaining = pos.sideToMove() == Position.WHITE ? whiteMs : blackMs;
        SearchResult result = s.search(pos, level.limits(remaining, incrementMs));
        return toDomain(board, level.choose(result, random));
    }

    /** Asks a search in progress to finish at once; its move is then the best found so far. */
    public void stop() {
        Searcher s = searcher;
        if (s != null) s.stop();
    }

    // ---- conversions -----------------------------------------------------------------------------

    /**
     * The engine position for {@code board}, with the game's earlier positions in its history so
     * the search sees repetitions. If the moves don't lead from the start to {@code board} the
     * history is dropped and the position is read from the board alone.
     */
    static Position positionWithHistory(Board board, Board startBoard, List<String> moves) {
        String current = FenParser.toFen(board);
        try {
            Position replayed = Position.fromFen(FenParser.toFen(startBoard));
            for (String uci : moves) {
                int move = replayed.parseUci(uci);
                if (move == com.chess.engine.core.Move.NONE) return Position.fromFen(current);
                replayed.makeMove(move);
            }
            return samePosition(replayed.toFen(), current) ? replayed : Position.fromFen(current);
        } catch (IllegalArgumentException e) {
            return Position.fromFen(current);
        }
    }

    /** Same pieces, side to move and castling rights (the first three FEN fields). */
    private static boolean samePosition(String a, String b) {
        String[] x = a.split(" "), y = b.split(" ");
        return x[0].equals(y[0]) && x[1].equals(y[1]) && x[2].equals(y[2]);
    }

    private static Move toDomain(Board board, int engineMove) {
        String uci = com.chess.engine.core.Move.toUci(engineMove);
        return MoveGenerator.generateLegalMoves(board).stream()
            .filter(m -> m.toUci().equals(uci))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("The engine chose " + uci + ", which is not legal here"));
    }
}
