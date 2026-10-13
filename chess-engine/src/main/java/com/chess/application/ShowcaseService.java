package com.chess.application;

import com.chess.api.dto.ShowcaseDto;
import com.chess.domain.board.Board;
import com.chess.domain.board.FenParser;
import com.chess.domain.model.Move;
import com.chess.domain.rules.GameStateChecker;
import com.chess.domain.rules.MoveGenerator;
import com.chess.domain.rules.SanFormatter;
import com.chess.engine.core.Position;
import com.chess.engine.core.eval.Evaluators;
import com.chess.engine.core.search.SearchInfo;
import com.chess.engine.core.search.SearchLimits;
import com.chess.engine.core.search.SearchResult;
import com.chess.engine.core.search.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The engine's analysis of the position the landing page is built around, so the page shows what
 * the engine really found rather than numbers someone typed in.
 *
 * <p>The story is Légal's trap (Paris, about 1750): 1.e4 e5 2.Bc4 d6 3.Nf3 Bg4 4.Nc3 g6, and White
 * plays 5.Nxe5, leaving the queen to be taken. Saint Brie took it, 5...Bxd1, and was mated:
 * 6.Bxf7+ Ke7 7.Nd5#. The engine searches the position after 5.Nxe5 for its best replies, and
 * separately the position after 5...Bxd1, to show what taking the queen would have led to.
 *
 * <p>The position never changes, so the search runs once (on a thread of its own, at a fixed depth so
 * its node counts don't depend on the machine) and every request after that is served from memory.
 * Nobody can make the server search anything else through this.
 */
@Service
public class ShowcaseService {

    private static final Logger log = LoggerFactory.getLogger(ShowcaseService.class);

    static final String TITLE = "Légal's trap, Paris, about 1750";
    /** 1.e4 e5 2.Bc4 d6 3.Nf3 Bg4 4.Nc3 g6, in UCI */
    static final List<String> OPENING = List.of("e2e4", "e7e5", "f1c4", "d7d6", "g1f3", "c8g4", "b1c3", "g7g6");
    /** 5.Nxe5: the move the visitor "sees" */
    static final String PLAYER_MOVE = "f3e5";
    /** 5...Bxd1: the queen, taken */
    static final String TRAP_MOVE = "g4d1";

    /** Plies searched. Fixed, so the counts are the same on every machine; only the time differs. */
    static final int DEPTH = 12;
    /** Replies weighed side by side */
    static final int LINES = 4;
    static final int TRAP_DEPTH = 8;
    private static final int HASH_MB = 16;

    private final boolean warmUp;
    private final Clock clock;
    private final int depth;
    private CompletableFuture<ShowcaseDto.Showcase> result;

    @Autowired
    public ShowcaseService(@Value("${app.showcase.warm-up:false}") boolean warmUp) {
        this(warmUp, Clock.systemUTC(), DEPTH);
    }

    /** For tests: a shallower search finishes in a fraction of the time */
    ShowcaseService(boolean warmUp, Clock clock, int depth) {
        this.warmUp = warmUp;
        this.clock = clock;
        this.depth = depth;
    }

    /** Production starts the search as soon as the server is up, so the first visitor doesn't wait for it. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (warmUp) showcase();
    }

    /**
     * The analysis, once it is ready. The first call starts the search; a search that failed is
     * tried again by the next call.
     */
    public synchronized CompletableFuture<ShowcaseDto.Showcase> showcase() {
        if (result == null || result.isCompletedExceptionally()) {
            CompletableFuture<ShowcaseDto.Showcase> f = new CompletableFuture<>();
            result = f;
            Thread t = new Thread(() -> {
                try {
                    f.complete(analyse());
                } catch (RuntimeException | Error e) {
                    log.error("Showcase analysis failed", e);
                    f.completeExceptionally(e);
                }
            }, "showcase-analysis");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);   // never in the way of the games being played
            t.start();
        }
        return result;
    }

    /** Runs both searches. Takes a few seconds; the public entry point is {@link #showcase()}. */
    ShowcaseDto.Showcase analyse() {
        Position start = Position.startPosition();
        for (String uci : OPENING) start.makeMove(engineMove(start, uci));
        String startFen = start.toFen();

        Position afterPlayer = Position.fromFen(startFen);
        afterPlayer.makeMove(engineMove(afterPlayer, PLAYER_MOVE));
        String fen = afterPlayer.toFen();

        // The engine's reply to the player's move: its best few, depth by depth
        List<ShowcaseDto.Iteration> iterations = new ArrayList<>();
        Searcher searcher = new Searcher(Evaluators.create(), HASH_MB);
        Position root = Position.fromFen(fen);
        SearchResult reply = searcher.search(root, SearchLimits.depth(depth).withMultiPv(LINES), info -> {
            // a depth is done when its last line has been searched; its first line is its best move
            if (info.multiPvIndex() == 1) iterations.add(iteration(fen, info));
            else iterations.set(iterations.size() - 1, withTotals(iterations.get(iterations.size() - 1), info));
        });
        if (reply.bestMove() == 0) throw new IllegalStateException("The showcase position has no legal move");

        List<ShowcaseDto.Line> lines = reply.lines().stream()
            .map(l -> new ShowcaseDto.Line(ref(fen, l.move()), whiteScore(fen, l.score()), san(fen, l.pv())))
            .toList();
        long nps = reply.timeMs() > 0 ? reply.nodes() * 1000 / reply.timeMs() : 0;
        ShowcaseDto.Search search = new ShowcaseDto.Search(
            ref(fen, reply.bestMove()), whiteScore(fen, reply.score()), reply.depth(),
            reply.nodes(), reply.timeMs(), nps, lines, List.copyOf(iterations));

        Position afterReply = Position.fromFen(fen);
        afterReply.makeMove(reply.bestMove());

        // What taking the queen leads to: the position after it, searched for White
        Position afterTrap = Position.fromFen(fen);
        afterTrap.makeMove(engineMove(afterTrap, TRAP_MOVE));
        String trapFen = afterTrap.toFen();
        SearchResult refutation = new Searcher(Evaluators.create(), HASH_MB)
            .search(Position.fromFen(trapFen), SearchLimits.depth(TRAP_DEPTH));
        ShowcaseDto.Trap trap = new ShowcaseDto.Trap(
            new ShowcaseDto.MoveRef(TRAP_MOVE, san(fen, TRAP_MOVE)),
            whiteScore(trapFen, refutation.score()), san(trapFen, refutation.pv()),
            refutation.depth(), refutation.nodes());

        log.info("Showcase analysed: {} at depth {}, {} positions in {} ms",
            search.bestMove().san(), search.depth(), search.nodes(), search.timeMs());
        return new ShowcaseDto.Showcase(
            TITLE, startFen, new ShowcaseDto.MoveRef(PLAYER_MOVE, san(startFen, PLAYER_MOVE)), fen,
            search, afterReply.toFen(), trap, Evaluators.describe(), clock.instant());
    }

    /* ── helpers ─────────────────────────────────────────────────────────── */

    private static ShowcaseDto.Iteration iteration(String fen, SearchInfo info) {
        int best = info.pv().length > 0 ? info.pv()[0] : 0;
        return new ShowcaseDto.Iteration(info.depth(), info.nodes(), info.timeMs(),
            best == 0 ? null : ref(fen, best), whiteScore(fen, info.score()));
    }

    /** The same depth, with the totals reached once its later lines are searched too */
    private static ShowcaseDto.Iteration withTotals(ShowcaseDto.Iteration it, SearchInfo info) {
        return new ShowcaseDto.Iteration(it.depth(), info.nodes(), info.timeMs(), it.bestMove(), it.score());
    }

    /** The engine's encoding of a legal move given in UCI */
    private static int engineMove(Position pos, String uci) {
        for (int m : pos.legalMoves()) {
            if (com.chess.engine.core.Move.toUci(m).equals(uci)) return m;
        }
        throw new IllegalStateException("Not a legal move in " + pos.toFen() + ": " + uci);
    }

    private static ShowcaseDto.MoveRef ref(String fen, int engineMove) {
        String uci = com.chess.engine.core.Move.toUci(engineMove);
        return new ShowcaseDto.MoveRef(uci, san(fen, uci));
    }

    /** SAN of one move, played from the position `fen` */
    static String san(String fen, String uci) {
        List<String> line = san(fen, List.of(uci));
        if (line.isEmpty()) throw new IllegalStateException("Not a legal move in " + fen + ": " + uci);
        return line.get(0);
    }

    private static List<String> san(String fen, int[] pv) {
        List<String> ucis = new ArrayList<>(pv.length);
        for (int m : pv) ucis.add(com.chess.engine.core.Move.toUci(m));
        return san(fen, ucis);
    }

    /** SAN of a line played from `fen`, as far as its moves are legal */
    static List<String> san(String fen, List<String> ucis) {
        Board board = FenParser.parse(fen);
        List<String> out = new ArrayList<>(ucis.size());
        for (String uci : ucis) {
            Move move = MoveGenerator.generateLegalMoves(board).stream()
                .filter(m -> m.toUci().equals(uci))
                .findFirst().orElse(null);
            if (move == null) break;
            Board after = board.apply(move);
            out.add(SanFormatter.format(board, move, GameStateChecker.evaluate(after, after.activeColor())));
            board = after;
        }
        return out;
    }

    /** A search score (for the side to move in `fen`) turned to White's point of view */
    static ShowcaseDto.Score whiteScore(String fen, int score) {
        boolean whiteToMove = fen.split(" ")[1].equals("w");
        int white = whiteToMove ? score : -score;
        if (Math.abs(white) >= Searcher.MATE_IN_MAX) {
            int moves = (Searcher.MATE - Math.abs(white) + 1) / 2;
            return new ShowcaseDto.Score(null, white > 0 ? moves : -moves);
        }
        return new ShowcaseDto.Score(white, null);
    }
}
