package com.chess.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class GameDto {

    public record Square(int row, int col) {}

    public record Piece(String type, String color) {}

    public record CastlingRights(
        boolean whiteKingside,
        boolean whiteQueenside,
        boolean blackKingside,
        boolean blackQueenside
    ) {}

    public record TimeControl(String type, long initialMs, long incrementMs) {}

    /** The rating changes are set only for finished online games. */
    public record GameResult(String winner, String reason,
                             Integer whiteEloChange, Integer blackEloChange) {
        public GameResult(String winner, String reason) { this(winner, reason, null, null); }
    }

    public record GamePlayer(
        String        userId,
        String        username,
        Integer       elo,
        String        color,
        long          timeRemainingMs,
        List<Piece>   capturedPieces,
        Boolean       isAi,
        Integer       aiDifficulty
    ) {}

    public record Move(
        Square  from,
        Square  to,
        Piece   piece,
        Piece   capturedPiece,
        String  promotion,
        Boolean isEnPassant,
        String  isCastling,
        Boolean isCheck,
        Boolean isCheckmate,
        String  algebraicNotation,
        Instant timestamp
    ) {}

    // Board is derived from FEN — we expose FEN and the parsed 8x8 grid
    public record BoardState(Piece[][] squares) {}

    public record Game(
        String        id,
        String        mode,
        String        status,
        GamePlayer    playerWhite,
        GamePlayer    playerBlack,
        BoardState    board,
        List<Move>    moves,
        String        currentTurn,
        TimeControl   timeControl,
        Square        enPassantTarget,
        CastlingRights castlingRights,
        int           halfMoveClock,
        int           fullMoveNumber,
        GameResult    result,
        String        opening,
        Instant       createdAt,
        Instant       updatedAt,
        // Extended fields for convenience
        String        fen,
        List<String>  legalMoves,
        List<String>  moveHistory,
        String        lastMove,
        String        drawOfferedBy      // "white" / "black" while an offer is pending, else null
    ) {
        public Game withDrawOfferedBy(String color) {
            return new Game(id, mode, status, playerWhite, playerBlack, board, moves, currentTurn,
                timeControl, enPassantTarget, castlingRights, halfMoveClock, fullMoveNumber,
                result, opening, createdAt, updatedAt, fen, legalMoves, moveHistory, lastMove, color);
        }
    }

    // POST /api/games request body
    public record CreateGameRequest(
        // Online games are only created by matchmaking or an accepted invitation, and a
        // saved game is loaded by id, not created
        @NotBlank @Pattern(regexp = "ai|local", message = "must be ai or local")
        String mode,
        Integer aiDifficulty,
        @Pattern(regexp = "(?i)white|black|random", message = "must be white, black or random")
        String  playerColor,
        @NotNull TimeControl timeControl,
        Boolean enableUndo,
        Boolean confirmMoves,
        Boolean showLegalMoves,
        Boolean realTimeAnalysis,
        String  savedGameId,
        String  fen             // custom starting position
    ) {
        /**
         * The same request with a "random" colour replaced by a concrete one, so the engine
         * and the database see the same side.
         *
         * @param userPlaysWhite decides the side when the colour is "random"
         */
        public CreateGameRequest withColorResolved(java.util.function.BooleanSupplier userPlaysWhite) {
            if (!"random".equalsIgnoreCase(playerColor)) return this;
            return new CreateGameRequest(mode, aiDifficulty,
                    userPlaysWhite.getAsBoolean() ? "white" : "black", timeControl,
                    enableUndo, confirmMoves, showLegalMoves, realTimeAnalysis, savedGameId, fen);
        }
    }

    // POST /api/games/{id}/moves
    public record MoveRequest(
        @NotBlank
        @Pattern(regexp = "[a-h][1-8][a-h][1-8][qrbnQRBN]?",
                 message = "move must be UCI format, e.g. e2e4 or e7e8q")
        String move
    ) {}

    public record SavedGame(
        String  id,
        String  gameId,
        String  opponentName,
        String  mode,
        int     turnNumber,
        String  playerColor,
        String  opening,
        Instant savedAt,
        String  thumbnailFen
    ) {}

    // Lightweight legal moves response
    public record LegalMovesResponse(
        String       gameId,
        String       activeColor,
        List<String> legalMoves
    ) {}
}
