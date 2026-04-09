package com.chess.infrastructure.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for creating a new game.
 *
 * All fields are optional; defaults apply when absent.
 *
 * @param fen       Starting position in FEN notation.
 *                  Defaults to the standard starting position.
 * @param aiColor   Which color the AI controls: "WHITE", "BLACK", or "NONE"
 *                  (human vs human). Defaults to "BLACK".
 * @param aiDepth   Alpha-beta search depth (1-6). Defaults to 4.
 */
@Schema(description = "Parameters for creating a new chess game")
public record CreateGameRequest(

    @Schema(description = "Starting FEN (omit for standard start)",
            example = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    String fen,

    @Schema(description = "Color controlled by the AI: WHITE, BLACK, or NONE",
            example = "BLACK", defaultValue = "BLACK")
    @Pattern(regexp = "WHITE|BLACK|NONE",
             message = "aiColor must be WHITE, BLACK, or NONE")
    String aiColor,

    @Schema(description = "AI search depth (1-6)", example = "4", defaultValue = "4")
    Integer aiDepth
) {
    /** Applies defaults for optional fields. */
    public CreateGameRequest {
        if (aiColor == null) aiColor = "BLACK";
        if (aiDepth == null) aiDepth = 4;
        if (aiDepth < 1 || aiDepth > 6)
            throw new IllegalArgumentException("aiDepth must be between 1 and 6");
    }
}
