package com.chess.engine.core;

import java.util.Arrays;

/**
 * A chess position that can be played forward and back quickly: twelve bitboards plus a board
 * array, with make and unmake instead of copying. The hash is the Polyglot key, kept up to
 * date move by move, so one number serves the transposition table, repetition detection and
 * an opening book.
 *
 * Not thread-safe: one search owns one position.
 */
public final class Position {

    // ---- pieces: color * 6 + type ---------------------------------------------------------
    public static final int WHITE = 0, BLACK = 1;
    public static final int PAWN = 0, KNIGHT = 1, BISHOP = 2, ROOK = 3, QUEEN = 4, KING = 5;
    public static final int NO_PIECE = 12;

    public static int piece(int color, int type) { return color * 6 + type; }
    public static int colorOf(int piece) { return piece / 6; }
    public static int typeOf(int piece) { return piece % 6; }

    // ---- castling rights -------------------------------------------------------------------
    public static final int WHITE_KING_SIDE = 1, WHITE_QUEEN_SIDE = 2, BLACK_KING_SIDE = 4, BLACK_QUEEN_SIDE = 8;

    /** Rights that survive a move touching each square (a king or rook leaving, or a rook taken). */
    private static final int[] CASTLE_MASK = new int[64];
    static {
        Arrays.fill(CASTLE_MASK, 15);
        CASTLE_MASK[0] = ~WHITE_QUEEN_SIDE & 15;
        CASTLE_MASK[4] = ~(WHITE_KING_SIDE | WHITE_QUEEN_SIDE) & 15;
        CASTLE_MASK[7] = ~WHITE_KING_SIDE & 15;
        CASTLE_MASK[56] = ~BLACK_QUEEN_SIDE & 15;
        CASTLE_MASK[60] = ~(BLACK_KING_SIDE | BLACK_QUEEN_SIDE) & 15;
        CASTLE_MASK[63] = ~BLACK_KING_SIDE & 15;
    }

    // ---- hash keys (Polyglot) --------------------------------------------------------------
    private static final long[][] PIECE_KEY = new long[12][64];
    private static final long[] CASTLE_KEY = new long[16];
    private static final long[] EP_KEY = new long[8];
    private static final long SIDE_KEY = PolyglotZobrist.RANDOM[780];
    static {
        long[] r = PolyglotZobrist.RANDOM;
        for (int color = 0; color < 2; color++) {
            for (int type = 0; type < 6; type++) {
                int kind = 2 * type + (color == WHITE ? 1 : 0);          // Polyglot orders black before white
                for (int sq = 0; sq < 64; sq++) PIECE_KEY[piece(color, type)][sq] = r[64 * kind + sq];
            }
        }
        for (int rights = 0; rights < 16; rights++) {
            long k = 0;
            if ((rights & WHITE_KING_SIDE) != 0) k ^= r[768];
            if ((rights & WHITE_QUEEN_SIDE) != 0) k ^= r[769];
            if ((rights & BLACK_KING_SIDE) != 0) k ^= r[770];
            if ((rights & BLACK_QUEEN_SIDE) != 0) k ^= r[771];
            CASTLE_KEY[rights] = k;
        }
        for (int f = 0; f < 8; f++) EP_KEY[f] = r[772 + f];
    }

    public static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    // ---- state -----------------------------------------------------------------------------
    private final long[] bb = new long[12];
    private final long[] occ = new long[2];
    private long occAll;
    private final byte[] board = new byte[64];

    private int side;
    private int castle;
    private int ep = -1;           // square behind a pawn that just moved two squares, else -1
    private int halfmove;
    private int fullmove = 1;
    private long key;

    // ---- history, for unmake and repetition ------------------------------------------------
    private int ply;
    private int[] hMove = new int[256];
    private int[] hCaptured = new int[256];
    private int[] hCastle = new int[256];
    private int[] hEp = new int[256];
    private int[] hHalf = new int[256];
    private long[] hKey = new long[256];

    private Position() {
        Arrays.fill(board, (byte) NO_PIECE);
    }

    // ============================================================================================
    //  Creating positions
    // ============================================================================================

    public static Position startPosition() { return fromFen(START_FEN); }

    /** @throws IllegalArgumentException if the text is not a usable position */
    public static Position fromFen(String fen) {
        String[] f = fen.trim().split("\\s+");
        if (f.length < 4) throw new IllegalArgumentException("FEN needs at least four fields: " + fen);
        Position p = new Position();

        String[] ranks = f[0].split("/");
        if (ranks.length != 8) throw new IllegalArgumentException("FEN needs eight ranks: " + fen);
        for (int i = 0; i < 8; i++) {
            int rank = 7 - i, file = 0;
            for (char c : ranks[i].toCharArray()) {
                if (c >= '1' && c <= '8') {
                    file += c - '0';
                } else {
                    int piece = pieceFromChar(c);
                    if (piece == NO_PIECE || file > 7) throw new IllegalArgumentException("Bad FEN piece placement: " + fen);
                    p.put(piece, Bits.square(file++, rank));
                }
            }
            if (file != 8) throw new IllegalArgumentException("Bad FEN rank width: " + fen);
        }
        if (Bits.count(p.bb[piece(WHITE, KING)]) != 1 || Bits.count(p.bb[piece(BLACK, KING)]) != 1)
            throw new IllegalArgumentException("Each side needs exactly one king: " + fen);

        p.side = switch (f[1]) {
            case "w" -> WHITE;
            case "b" -> BLACK;
            default -> throw new IllegalArgumentException("Bad side to move: " + fen);
        };
        for (char c : f[2].toCharArray()) {
            switch (c) {
                case 'K' -> p.castle |= WHITE_KING_SIDE;
                case 'Q' -> p.castle |= WHITE_QUEEN_SIDE;
                case 'k' -> p.castle |= BLACK_KING_SIDE;
                case 'q' -> p.castle |= BLACK_QUEEN_SIDE;
                case '-' -> { }
                default -> throw new IllegalArgumentException("Bad castling rights: " + fen);
            }
        }
        p.ep = f[3].equals("-") ? -1 : Bits.parse(f[3]);
        if (!f[3].equals("-") && p.ep < 0) throw new IllegalArgumentException("Bad en passant square: " + fen);
        p.halfmove = f.length > 4 ? Integer.parseInt(f[4]) : 0;
        p.fullmove = f.length > 5 ? Integer.parseInt(f[5]) : 1;
        p.key = p.computeKey();
        return p;
    }

    public Position copy() {
        Position c = new Position();
        System.arraycopy(bb, 0, c.bb, 0, 12);
        System.arraycopy(occ, 0, c.occ, 0, 2);
        c.occAll = occAll;
        System.arraycopy(board, 0, c.board, 0, 64);
        c.side = side; c.castle = castle; c.ep = ep; c.halfmove = halfmove; c.fullmove = fullmove; c.key = key;
        c.ply = ply;
        c.hMove = hMove.clone(); c.hCaptured = hCaptured.clone(); c.hCastle = hCastle.clone();
        c.hEp = hEp.clone(); c.hHalf = hHalf.clone(); c.hKey = hKey.clone();
        return c;
    }

    private static int pieceFromChar(char c) {
        int color = Character.isUpperCase(c) ? WHITE : BLACK;
        return switch (Character.toLowerCase(c)) {
            case 'p' -> piece(color, PAWN);
            case 'n' -> piece(color, KNIGHT);
            case 'b' -> piece(color, BISHOP);
            case 'r' -> piece(color, ROOK);
            case 'q' -> piece(color, QUEEN);
            case 'k' -> piece(color, KING);
            default -> NO_PIECE;
        };
    }

    private static final String PIECE_CHARS = "PNBRQKpnbrqk";

    /** The position as FEN. The en passant square is shown after every double push. */
    public String toFen() {
        StringBuilder sb = new StringBuilder(80);
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int p = board[Bits.square(file, rank)];
                if (p == NO_PIECE) { empty++; continue; }
                if (empty > 0) { sb.append(empty); empty = 0; }
                sb.append(PIECE_CHARS.charAt(p));
            }
            if (empty > 0) sb.append(empty);
            if (rank > 0) sb.append('/');
        }
        sb.append(side == WHITE ? " w " : " b ");
        if (castle == 0) sb.append('-');
        else {
            if ((castle & WHITE_KING_SIDE) != 0) sb.append('K');
            if ((castle & WHITE_QUEEN_SIDE) != 0) sb.append('Q');
            if ((castle & BLACK_KING_SIDE) != 0) sb.append('k');
            if ((castle & BLACK_QUEEN_SIDE) != 0) sb.append('q');
        }
        sb.append(' ').append(ep < 0 ? "-" : Bits.name(ep));
        sb.append(' ').append(halfmove).append(' ').append(fullmove);
        return sb.toString();
    }

    @Override public String toString() { return toFen(); }

    // ============================================================================================
    //  Reading the position
    // ============================================================================================

    public int sideToMove() { return side; }
    public int pieceAt(int square) { return board[square]; }
    public long bitboard(int piece) { return bb[piece]; }
    public long bitboard(int color, int type) { return bb[piece(color, type)]; }
    public long occupancy(int color) { return occ[color]; }
    public long occupied() { return occAll; }
    public int castlingRights() { return castle; }
    public int epSquare() { return ep; }
    public int halfmoveClock() { return halfmove; }
    public int fullmoveNumber() { return fullmove; }
    /** The Polyglot hash of the position. */
    public long key() { return key; }
    /** How many moves have been made on this position since it was created. */
    public int ply() { return ply; }

    public int kingSquare(int color) { return Bits.lsb(bb[piece(color, KING)]); }

    /** Whether any piece of {@code byColor} attacks {@code square}, given the current occupancy. */
    public boolean isAttacked(int square, int byColor) {
        return (Attacks.PAWN[byColor ^ 1][square] & bb[piece(byColor, PAWN)]) != 0
            || (Attacks.KNIGHT[square] & bb[piece(byColor, KNIGHT)]) != 0
            || (Attacks.KING[square] & bb[piece(byColor, KING)]) != 0
            || (Attacks.bishop(square, occAll) & (bb[piece(byColor, BISHOP)] | bb[piece(byColor, QUEEN)])) != 0
            || (Attacks.rook(square, occAll) & (bb[piece(byColor, ROOK)] | bb[piece(byColor, QUEEN)])) != 0;
    }

    /** Every piece, of either colour, that attacks {@code square} through occupancy {@code occupied}. */
    public long attackersTo(int square, long occupied) {
        return (Attacks.PAWN[BLACK][square] & bb[piece(WHITE, PAWN)])
             | (Attacks.PAWN[WHITE][square] & bb[piece(BLACK, PAWN)])
             | (Attacks.KNIGHT[square] & (bb[piece(WHITE, KNIGHT)] | bb[piece(BLACK, KNIGHT)]))
             | (Attacks.KING[square] & (bb[piece(WHITE, KING)] | bb[piece(BLACK, KING)]))
             | (Attacks.bishop(square, occupied) & (bb[piece(WHITE, BISHOP)] | bb[piece(BLACK, BISHOP)]
                                                   | bb[piece(WHITE, QUEEN)] | bb[piece(BLACK, QUEEN)]))
             | (Attacks.rook(square, occupied) & (bb[piece(WHITE, ROOK)] | bb[piece(BLACK, ROOK)]
                                                 | bb[piece(WHITE, QUEEN)] | bb[piece(BLACK, QUEEN)]));
    }

    public boolean inCheck() { return isAttacked(kingSquare(side), side ^ 1); }

    // ============================================================================================
    //  Draws that don't depend on the legal moves
    // ============================================================================================

    /** True if the position has occurred before since the last capture or pawn move. */
    public boolean isRepetition() {
        int back = Math.min(halfmove, ply);
        for (int i = 2; i <= back; i += 2) {
            if (hKey[ply - i] == key) return true;
        }
        return false;
    }

    public boolean isFiftyMoveDraw() { return halfmove >= 100; }

    /**
     * Neither side can ever mate: bare kings, a lone minor piece, or only bishops that all sit
     * on one colour of square.
     */
    public boolean isInsufficientMaterial() {
        if (((bb[piece(WHITE, PAWN)] | bb[piece(BLACK, PAWN)]
            | bb[piece(WHITE, ROOK)] | bb[piece(BLACK, ROOK)]
            | bb[piece(WHITE, QUEEN)] | bb[piece(BLACK, QUEEN)])) != 0) return false;
        long knights = bb[piece(WHITE, KNIGHT)] | bb[piece(BLACK, KNIGHT)];
        long bishops = bb[piece(WHITE, BISHOP)] | bb[piece(BLACK, BISHOP)];
        int minors = Bits.count(knights | bishops);
        if (minors <= 1) return true;
        if (knights != 0) return false;
        // only bishops: a draw if every one is on the same colour of square
        long lightSquares = 0x55AA55AA55AA55AAL;
        return (bishops & lightSquares) == 0 || (bishops & ~lightSquares) == 0;
    }

    // ============================================================================================
    //  Making and unmaking moves
    // ============================================================================================

    private void put(int piece, int sq) {
        long b = 1L << sq;
        bb[piece] |= b;
        occ[piece / 6] |= b;
        occAll |= b;
        board[sq] = (byte) piece;
    }

    private void take(int piece, int sq) {
        long b = ~(1L << sq);
        bb[piece] &= b;
        occ[piece / 6] &= b;
        occAll &= b;
        board[sq] = (byte) NO_PIECE;
    }

    private long epKey(int epSquare, int sideToMove) {
        if (epSquare < 0) return 0;
        // Polyglot hashes the square only when a pawn of the side to move could take on it
        return (Attacks.PAWN[sideToMove ^ 1][epSquare] & bb[piece(sideToMove, PAWN)]) != 0
            ? EP_KEY[Bits.file(epSquare)] : 0;
    }

    private long computeKey() {
        long k = 0;
        for (int sq = 0; sq < 64; sq++) {
            if (board[sq] != NO_PIECE) k ^= PIECE_KEY[board[sq]][sq];
        }
        k ^= CASTLE_KEY[castle];
        k ^= epKey(ep, side);
        if (side == WHITE) k ^= SIDE_KEY;
        return k;
    }

    private void grow() {
        int n = hMove.length * 2;
        hMove = Arrays.copyOf(hMove, n); hCaptured = Arrays.copyOf(hCaptured, n);
        hCastle = Arrays.copyOf(hCastle, n); hEp = Arrays.copyOf(hEp, n);
        hHalf = Arrays.copyOf(hHalf, n); hKey = Arrays.copyOf(hKey, n);
    }

    /** Plays a pseudo-legal move. The caller checks that it didn't leave its own king in check. */
    public void makeMove(int move) {
        if (ply == hMove.length) grow();
        final int from = Move.from(move), to = Move.to(move), flag = Move.flag(move);
        final int us = side, them = us ^ 1;
        final int moving = board[from];

        hMove[ply] = move; hCastle[ply] = castle; hEp[ply] = ep; hHalf[ply] = halfmove; hKey[ply] = key;
        int captured = NO_PIECE;

        key ^= epKey(ep, us);
        ep = -1;
        halfmove++;

        if (flag == Move.EP_CAPTURE) {
            int capSq = us == WHITE ? to - 8 : to + 8;
            captured = board[capSq];
            take(captured, capSq);
            key ^= PIECE_KEY[captured][capSq];
        } else if ((flag & 4) != 0) {
            captured = board[to];
            take(captured, to);
            key ^= PIECE_KEY[captured][to];
        }
        if (captured != NO_PIECE || typeOf(moving) == PAWN) halfmove = 0;
        hCaptured[ply] = captured;

        take(moving, from);
        key ^= PIECE_KEY[moving][from];
        int placed = Move.isPromotion(move) ? piece(us, Move.promotionType(move)) : moving;
        put(placed, to);
        key ^= PIECE_KEY[placed][to];

        if (flag == Move.KING_CASTLE) {
            int rookFrom = us == WHITE ? 7 : 63, rookTo = us == WHITE ? 5 : 61, rook = piece(us, ROOK);
            take(rook, rookFrom); put(rook, rookTo);
            key ^= PIECE_KEY[rook][rookFrom] ^ PIECE_KEY[rook][rookTo];
        } else if (flag == Move.QUEEN_CASTLE) {
            int rookFrom = us == WHITE ? 0 : 56, rookTo = us == WHITE ? 3 : 59, rook = piece(us, ROOK);
            take(rook, rookFrom); put(rook, rookTo);
            key ^= PIECE_KEY[rook][rookFrom] ^ PIECE_KEY[rook][rookTo];
        } else if (flag == Move.DOUBLE_PUSH) {
            ep = us == WHITE ? from + 8 : from - 8;
        }

        int newCastle = castle & CASTLE_MASK[from] & CASTLE_MASK[to];
        key ^= CASTLE_KEY[castle] ^ CASTLE_KEY[newCastle];
        castle = newCastle;

        side = them;
        key ^= SIDE_KEY;
        key ^= epKey(ep, them);
        if (us == BLACK) fullmove++;
        ply++;
    }

    public void unmakeMove() {
        ply--;
        final int move = hMove[ply];
        final int from = Move.from(move), to = Move.to(move), flag = Move.flag(move);
        final int us = side ^ 1;
        final int captured = hCaptured[ply];

        side = us;
        castle = hCastle[ply]; ep = hEp[ply]; halfmove = hHalf[ply]; key = hKey[ply];
        if (us == BLACK) fullmove--;

        int placed = board[to];
        take(placed, to);
        put(Move.isPromotion(move) ? piece(us, PAWN) : placed, from);

        if (flag == Move.EP_CAPTURE) {
            put(captured, us == WHITE ? to - 8 : to + 8);
        } else if (captured != NO_PIECE) {
            put(captured, to);
        } else if (flag == Move.KING_CASTLE) {
            int rookFrom = us == WHITE ? 7 : 63, rookTo = us == WHITE ? 5 : 61, rook = piece(us, ROOK);
            take(rook, rookTo); put(rook, rookFrom);
        } else if (flag == Move.QUEEN_CASTLE) {
            int rookFrom = us == WHITE ? 0 : 56, rookTo = us == WHITE ? 3 : 59, rook = piece(us, ROOK);
            take(rook, rookTo); put(rook, rookFrom);
        }
    }

    /** Passes the turn without moving. Only for search heuristics; the position stays hashable. */
    public void makeNullMove() {
        if (ply == hMove.length) grow();
        hMove[ply] = Move.NONE; hCastle[ply] = castle; hEp[ply] = ep; hHalf[ply] = halfmove; hKey[ply] = key;
        hCaptured[ply] = NO_PIECE;
        key ^= epKey(ep, side);
        ep = -1;
        side ^= 1;
        key ^= SIDE_KEY;
        halfmove++;
        ply++;
    }

    public void unmakeNullMove() {
        ply--;
        side ^= 1;
        castle = hCastle[ply]; ep = hEp[ply]; halfmove = hHalf[ply]; key = hKey[ply];
    }

    /** The last move made, or {@link Move#NONE} (also for a null move or before any move). */
    public int lastMove() { return ply == 0 ? Move.NONE : hMove[ply - 1]; }

    /** The piece captured by the last move, or {@link #NO_PIECE}. */
    public int lastCaptured() { return ply == 0 ? NO_PIECE : hCaptured[ply - 1]; }

    // ============================================================================================
    //  Convenience for code that isn't on the hot path
    // ============================================================================================

    /** All legal moves, as a fresh array. */
    public int[] legalMoves() {
        int[] buffer = new int[256];
        int n = MoveGen.legal(this, buffer);
        return Arrays.copyOf(buffer, n);
    }

    /** The legal move written as UCI text ("e2e4", "e7e8q"), or {@link Move#NONE} if there is none. */
    public int parseUci(String uci) {
        for (int m : legalMoves()) {
            if (Move.toUci(m).equals(uci)) return m;
        }
        return Move.NONE;
    }
}
