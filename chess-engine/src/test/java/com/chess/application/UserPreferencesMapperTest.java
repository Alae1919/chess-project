package com.chess.application;

import com.chess.api.dto.UserDto;
import com.chess.persistence.entity.UserEntity;
import com.chess.persistence.entity.UserPreferencesEntity;
import com.chess.persistence.entity.UserPreferencesEntity.BoardTheme;
import com.chess.persistence.entity.UserPreferencesEntity.PieceStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserPreferencesMapper")
class UserPreferencesMapperTest {

    private final UserPreferencesMapper mapper = new UserPreferencesMapperImpl();

    private static UserDto.UpdatePreferencesRequest request(String boardTheme, String pieceStyle) {
        return new UserDto.UpdatePreferencesRequest(boardTheme, pieceStyle,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    private static UserPreferencesEntity defaults() {
        return UserPreferencesEntity.defaultsFor(new UserEntity());
    }

    @Test
    @DisplayName("every board theme survives a trip out through the DTO and back in")
    void boardThemesRoundTrip() {
        for (BoardTheme theme : BoardTheme.values()) {
            UserPreferencesEntity entity = defaults();
            entity.setBoardTheme(theme);
            String sent = mapper.toDto(entity).boardTheme();

            UserPreferencesEntity updated = defaults();
            mapper.updateEntity(updated, request(sent, null));

            assertEquals(theme, updated.getBoardTheme(), "theme sent to the client as '" + sent + "'");
        }
    }

    @Test
    @DisplayName("every piece style survives a trip out through the DTO and back in")
    void pieceStylesRoundTrip() {
        for (PieceStyle style : PieceStyle.values()) {
            UserPreferencesEntity entity = defaults();
            entity.setPieceStyle(style);
            String sent = mapper.toDto(entity).pieceStyle();

            UserPreferencesEntity updated = defaults();
            mapper.updateEntity(updated, request(null, sent));

            assertEquals(style, updated.getPieceStyle(), "style sent to the client as '" + sent + "'");
        }
    }

    @Test
    @DisplayName("the hyphenated names the client sends are accepted")
    void acceptsClientNames() {
        UserPreferencesEntity updated = defaults();

        mapper.updateEntity(updated, request("marble-green", "minimalist"));

        assertEquals(BoardTheme.marble_green, updated.getBoardTheme());
        assertEquals(PieceStyle.minimalist, updated.getPieceStyle());
    }

    @Test
    @DisplayName("an unknown theme is refused without leaking class names")
    void unknownThemeIsRefusedCleanly() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.updateEntity(defaults(), request("neon", null)));

        assertFalse(ex.getMessage().contains("com.chess"), ex.getMessage());
        assertTrue(ex.getMessage().contains("neon"), ex.getMessage());
    }

    @Test
    @DisplayName("fields left out of the request keep their value")
    void omittedFieldsAreKept() {
        UserPreferencesEntity entity = defaults();
        entity.setBoardTheme(BoardTheme.slate);

        mapper.updateEntity(entity, request(null, "modern"));

        assertEquals(BoardTheme.slate, entity.getBoardTheme());
        assertEquals(PieceStyle.modern, entity.getPieceStyle());
    }
}
