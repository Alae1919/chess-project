package com.chess.application;

import com.chess.api.dto.UserDto;
import com.chess.persistence.entity.EloHistoryEntity;
import com.chess.persistence.entity.UserAchievementEntity;
import com.chess.persistence.entity.UserEntity;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {UserPreferencesMapper.class})
public interface UserMapper {

    @Mapping(target = "id", expression = "java(entity.getId().toString())")
    @Mapping(target = "memberSince", source = "entity.createdAt")
    @Mapping(target = "achievements", source = "achievements")
    @Mapping(target = "subscription", expression = "java(toSubscriptionDto(entity))")
    @Mapping(target = "stats", expression = "java(mapStats(entity, statsMapper, eloHistory))")
    UserDto.User toDto(
        UserEntity entity, 
        List<UserAchievementEntity> achievements, 
        List<EloHistoryEntity> eloHistory,
        @Context StatsMapper statsMapper
    );

    @Mapping(target = "id", source = "definition.id")
    @Mapping(target = "icon", source = "definition.icon")
    @Mapping(target = "name", source = "definition.name")
    @Mapping(target = "description", source = "definition.description")
    @Mapping(target = "target", source = "definition.target")
    UserDto.Achievement toAchievementDto(UserAchievementEntity achievement);

    default String map(Enum<?> e) {
        return e == null ? null : e.name().toLowerCase().replace("_", "-");
    }

    default UserDto.Subscription toSubscriptionDto(UserEntity entity) {
        if (entity == null) return null;
        return new UserDto.Subscription(
            map(entity.getSubscriptionPlan()),
            entity.isSubscriptionActive(),
            entity.getSubscriptionRenewsAt()
        );
    }

    default UserDto.UserStats mapStats(
        UserEntity entity,
        @Context StatsMapper statsMapper,
        @Context List<EloHistoryEntity> eloHistory
    ) {
        return statsMapper.toDto(entity, eloHistory);
    }
}