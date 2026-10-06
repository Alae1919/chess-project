package com.chess.api.dto;

public final class EvaluationDto {

    public record PositionEvaluation(
        int    score,       // centipawns
        int    depth,
        String bestMove,    // algebraic
        String openingName
    ) {}
}


// ─── SECURITY ────────────────────────────────────────────────────────────────
