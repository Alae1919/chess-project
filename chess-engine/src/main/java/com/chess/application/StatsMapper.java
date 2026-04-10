package com.chess.application;

import com.chess.api.dto.UserDto;
import com.chess.persistence.entity.EloHistoryEntity;
import com.chess.persistence.entity.UserEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StatsMapper {

    default UserDto.UserStats toDto(UserEntity u, List<EloHistoryEntity> elo) {
        if (u == null) return null;
        int games = u.getGamesPlayed();
        double winRate = games > 0 ? (double) u.getWins() / games * 100 : 0.0;
        return new UserDto.UserStats(
            games, u.getWins(), u.getLosses(), u.getDraws(),
            Math.round(winRate * 10.0) / 10.0,
            u.getCurrentStreak(), u.getBestStreak(),
            elo != null ? elo.stream().map(e -> new UserDto.EloPoint(e.getRecordedAt(), e.getElo())).toList() : List.of()
        );
    }
}