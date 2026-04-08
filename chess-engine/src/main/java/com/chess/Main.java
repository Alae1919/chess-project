package com.chess;

import com.chess.application.GameService;
import com.chess.domain.model.Color;
import com.chess.engine.player.AiPlayer;
import com.chess.infrastructure.console.ConsoleAdapter;

public class Main {
    public static void main(String[] args) {
        // Example: AI (WHITE) vs AI (BLACK)
        // Swap AiPlayer with HumanPlayer for human interaction
        ConsoleAdapter console = new ConsoleAdapter();
        GameService game = new GameService(
            new AiPlayer(Color.WHITE),
            new AiPlayer(Color.BLACK),
            console
        );
        game.play();
    }
}
