package com.chess.infrastructure.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * List of legal moves for the active player.
 * Subset of GameStateResponse — useful for lightweight polling.
 */
@Schema(description = "Legal moves available to the active player")
public record LegalMovesResponse(
    String gameId,
    String activeColor,
    @Schema(example = "[\"e7e5\",\"d7d5\",\"g8f6\"]")
    List<String> legalMoves
) {}
