package com.chess.infrastructure.console;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;
import com.chess.domain.model.Move;
import com.chess.domain.rules.GameStateChecker.State;
import com.chess.shared.port.OutputPort;

import java.util.Scanner;

/**
 * Console-based implementation of OutputPort.
 *
 * Replaces the Scanner embedded in Joueur and the System.out.println
 * calls scattered across Chess, Echiquier, and Pion.
 */
public final class ConsoleAdapter implements OutputPort {

    private final Scanner scanner = new Scanner(System.in);

    @Override
    public void displayBoard(Board board) {
        System.out.println(board);
    }

    @Override
    public void displayMove(Move move) {
        System.out.println("Move played: " + move);
    }

    @Override
    public void displayCheck(Color colorInCheck) {
        System.out.println(colorInCheck + " is in check!");
    }

    @Override
    public void displayResult(State state, Color loser) {
        switch (state) {
            case CHECKMATE  -> System.out.println("Checkmate! " + loser.opposite() + " wins.");
            case STALEMATE  -> System.out.println("Stalemate — draw.");
            case DRAW_50_MOVE -> System.out.println("Draw by 50-move rule.");
            default -> System.out.println("Game over.");
        }
    }

    /**
     * Reads a move in UCI format from stdin (e.g. "e2e4", "e7e8q").
     * Returns null if the input cannot be parsed.
     */
    public String readMove(Color color) {
        System.out.print(color + " to move (e.g. e2e4): ");
        return scanner.nextLine().trim().toLowerCase();
    }
}
