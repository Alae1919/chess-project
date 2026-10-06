package com.chess.application;

import com.chess.api.dto.UserDto;
import com.chess.persistence.entity.UserPreferencesEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserPreferencesMapper {

    @Mapping(target = "boardTheme", source = "boardTheme", qualifiedByName = "mapBoardThemeToString")
    @Mapping(target = "pieceStyle", source = "pieceStyle", qualifiedByName = "mapPieceStyleToString")
    UserDto.UserPreferences toDto(UserPreferencesEntity entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "boardTheme", source = "boardTheme", qualifiedByName = "mapStringToBoardTheme")
    @Mapping(target = "pieceStyle", source = "pieceStyle", qualifiedByName = "mapStringToPieceStyle")
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "userId", ignore = true)
    void updateEntity(
        @MappingTarget UserPreferencesEntity entity,
        UserDto.UpdatePreferencesRequest dto
    );

    @Named("mapBoardThemeToString")
    default String map(UserPreferencesEntity.BoardTheme value) {
        return value == null ? null : value.name().toLowerCase().replace("_", "-");
    }

    @Named("mapPieceStyleToString")
    default String map(UserPreferencesEntity.PieceStyle value) {
        return value == null ? null : value.name().toLowerCase().replace("_", "-");
    }

    @Named("mapStringToBoardTheme")
    default UserPreferencesEntity.BoardTheme mapBoardTheme(String value) {
        return value == null ? null : parseEnum(UserPreferencesEntity.BoardTheme.class, value, "board theme");
    }

    @Named("mapStringToPieceStyle")
    default UserPreferencesEntity.PieceStyle mapPieceStyle(String value) {
        return value == null ? null : parseEnum(UserPreferencesEntity.PieceStyle.class, value, "piece style");
    }

    /**
     * Reads the client's hyphenated name ("classic-wood") as the lowercase, underscored
     * constant ("classic_wood"). The error names the value, never the Java class.
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String what) {
        String constant = value.replace('-', '_').toLowerCase(java.util.Locale.ROOT);
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equals(constant)) return candidate;
        }
        throw new IllegalArgumentException("Unknown " + what + ": " + value);
    }
}
