package com.chess.api.controller;

import com.chess.ChessApplication;
import com.chess.persistence.entity.UserEntity;
import com.chess.persistence.entity.UserPreferencesEntity;
import com.chess.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The profile endpoints against the real service and database. The app keeps no
 * session open for lazy loading (open-in-view is off), so these also prove that
 * every read here fetches what it needs.
 */
@SpringBootTest(classes = ChessApplication.class)
@AutoConfigureMockMvc
@WithMockUser(username = "uc_test_user")
@DisplayName("UserController — profile and preferences")
class UserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void ensureUserWithDefaultPreferences() {
        if (users.findByUsername("uc_test_user").isEmpty()) {
            var u = new UserEntity();
            u.setUsername("uc_test_user");
            u.setEmail("uc_test_user@example.com");
            u.setPasswordHash("unused");
            u.setPreferences(UserPreferencesEntity.defaultsFor(u));
            users.save(u);
        }
        // The database keeps its rows between runs, so every test starts from the default look
        jdbc.update("update user_preferences set board_theme = cast('classic_wood' as board_theme), "
                  + "piece_style = cast('standard' as piece_style) "
                  + "where user_id = (select id from users where username = 'uc_test_user')");
    }

    @Test
    @DisplayName("GET /me returns the profile with its preferences")
    void profile() throws Exception {
        mvc.perform(get("/api/users/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("uc_test_user"))
            .andExpect(jsonPath("$.preferences.boardTheme").value("classic-wood"))
            .andExpect(jsonPath("$.preferences.pieceStyle").value("standard"));
    }

    @Test
    @DisplayName("the stats, achievements and rating history can be read")
    void readEndpoints() throws Exception {
        mvc.perform(get("/api/users/me/stats")).andExpect(status().isOk());
        mvc.perform(get("/api/users/me/achievements")).andExpect(status().isOk());
        mvc.perform(get("/api/users/me/elo-history")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a board theme and piece style can be changed, and stay changed")
    void themeAndStyleCanBeSaved() throws Exception {
        mvc.perform(patch("/api/users/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"boardTheme\":\"marble-green\",\"pieceStyle\":\"modern\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.boardTheme").value("marble-green"))
            .andExpect(jsonPath("$.pieceStyle").value("modern"));

        mvc.perform(get("/api/users/me"))
            .andExpect(jsonPath("$.preferences.boardTheme").value("marble-green"))
            .andExpect(jsonPath("$.preferences.pieceStyle").value("modern"));
    }

    @Test
    @DisplayName("an unknown theme is a 400 that doesn't name internal classes")
    void unknownThemeIsRefused() throws Exception {
        mvc.perform(patch("/api/users/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"boardTheme\":\"neon\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", not(containsString("com.chess"))));
    }
}
