package com.chess.api.dto;

import java.time.Instant;
import java.util.List;

public final class MatchHistoryDto {

    public record MatchHistory(
        String  id,
        String  opponentUsername,
        String  mode,
        String  timeControlLabel,
        String  result,
        String  playerColor,
        int     movesCount,
        Integer eloDelta,
        Instant playedAt
    ) {}

    public record PagedMatchHistory(
        List<MatchHistory> content,
        int  page,
        int  size,
        long totalElements,
        int  totalPages
    ) {}
}
