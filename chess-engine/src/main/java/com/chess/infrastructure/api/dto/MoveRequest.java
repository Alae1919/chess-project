package com.chess.infrastructure.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * A move submitted by a human player, in UCI format.
 *
 * Examples:
 *   "e2e4"    — normal move
 *   "e7e8q"   — pawn promotion to queen
 *   "e1g1"    — king-side castling
 */
@Schema(description = "A move in UCI format (e.g. e2e4, e7e8q)")
public record MoveRequest(

    @NotBlank(message = "move must not be blank")
    @Pattern(regexp = "[a-h][1-8][a-h][1-8][qrbnQRBN]?",
             message = "move must be in UCI format: e.g. e2e4 or e7e8q")
    @Schema(description = "UCI move string", example = "e2e4", required = true)
    String move
) {}
