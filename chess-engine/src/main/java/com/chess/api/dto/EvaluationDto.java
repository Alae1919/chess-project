package com.chess.api.dto;

import jakarta.validation.constraints.*;

public final class EvaluationDto {

    public record EvaluateRequest(
        @NotBlank String fen,
        @Min(1) @Max(8) int depth
    ) {}

    public record PositionEvaluation(
        int    score,       // centipawns
        int    depth,
        String bestMove,    // algebraic
        String openingName
    ) {}
}


// ─── SECURITY ────────────────────────────────────────────────────────────────
