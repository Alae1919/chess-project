package com.chess.infrastructure.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Complete snapshot of a game, as returned by the API.
 *
 * The domain's Board, Move, and Color types never appear here —
 * all data is serialised to primitive-friendly Java types.
 *
 * @param gameId        UUID of the game.
 * @param fen           Current board in FEN notation.
 * @param activeColor   Whose turn it is: "WHITE" or "BLACK".
 * @param status        Game status, including ONGOING, CHECK, CHECKMATE, STALEMATE,
 *                      DRAW_50_MOVE, or session outcomes WHITE_RESIGNED, BLACK_RESIGNED, DRAW_AGREED.
 * @param lastMove      Last move played in UCI format, or null if no move played yet.
 * @param moveHistory   Full list of moves played, oldest first, in UCI format.
 * @param legalMoves    All legal moves for the active player in UCI format.
 *                      Empty when the game is over.
 */
@Schema(description = "Full game state snapshot")
public record GameStateResponse(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    String gameId,

    @Schema(example = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
    String fen,

    @Schema(example = "BLACK")
    String activeColor,

    @Schema(example = "ONGOING",
            allowableValues = {
                "ONGOING", "CHECK", "CHECKMATE", "STALEMATE", "DRAW_50_MOVE",
                "WHITE_RESIGNED", "BLACK_RESIGNED", "DRAW_AGREED"
            })
    String status,

    @Schema(example = "e2e4", nullable = true)
    String lastMove,

    @Schema(example = "[\"e2e4\"]")
    List<String> moveHistory,

    @Schema(example = "[\"e7e5\",\"d7d5\"]")
    List<String> legalMoves
) {}
