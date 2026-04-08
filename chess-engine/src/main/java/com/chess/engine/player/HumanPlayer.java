package com.chess.engine.player;

import com.chess.domain.board.Board;
import com.chess.domain.model.*;
import com.chess.domain.rules.MoveGenerator;
import com.chess.domain.rules.MoveValidator;
import com.chess.infrastructure.console.ConsoleAdapter;

import java.util.List;

/**
 * A human player that reads moves from the console.
 *
 * Promotion: when a pawn reaches the back rank, the player is asked to
 * choose the promotion piece. This replaces the Scanner inside Pion.java,
 * keeping I/O entirely in the infrastructure layer.
 */
public final class HumanPlayer implements Player {

    private final Color color;
    private final ConsoleAdapter console;

    public HumanPlayer(Color color, ConsoleAdapter console) {
        this.color   = color;
        this.console = console;
    }

    @Override
    public Move chooseMove(Board board) {
        List<Move> legal = MoveGenerator.generateLegalMoves(board, color);
        while (true) {
            String input = console.readMove(color);
            Move candidate = parseInput(input, legal, board);
            if (candidate != null) return candidate;
            System.out.println("Illegal move. Try again (format: e2e4).");
        }
    }

    @Override
    public Color color() { return color; }

    /**
     * Parses UCI-format input and finds the matching legal move.
     * Handles promotion suffix (e7e8q).
     */
    private Move parseInput(String input, List<Move> legal, Board board) {
        if (input.length() < 4) return null;
        try {
            Square from = Square.of(input.substring(0, 2));
            Square to   = Square.of(input.substring(2, 4));
            PieceType promo = null;
            if (input.length() == 5) {
                promo = switch (input.charAt(4)) {
                    case 'q' -> PieceType.QUEEN;
                    case 'r' -> PieceType.ROOK;
                    case 'b' -> PieceType.BISHOP;
                    case 'n' -> PieceType.KNIGHT;
                    default  -> null;
                };
            }
            final PieceType finalPromo = promo;
            return legal.stream()
                .filter(m -> m.from().equals(from) && m.to().equals(to)
                    && (finalPromo == null || finalPromo == m.promotion()))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}


