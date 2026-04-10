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

    public record GameResult(String winner, String reason) {}

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
        List<String>  legalMoves
    ) {}

    // POST /api/games request body
    public record CreateGameRequest(
        @NotBlank String mode,
        Integer aiDifficulty,
        String  playerColor,
        @NotNull TimeControl timeControl,
        Boolean enableUndo,
        Boolean confirmMoves,
        Boolean showLegalMoves,
        Boolean realTimeAnalysis,
        String  savedGameId,
        String  fen             // custom starting position
    ) {}

    // POST /api/games/{id}/moves
    public record MoveRequest(
        @NotBlank
        @Pattern(regexp = "[a-h][1-8][a-h][1-8][qrbnQRBN]?",
                 message = "move must be UCI format, e.g. e2e4 or e7e8q")
        String move
    ) {}

    public record SavedGame(
        String  id,
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
