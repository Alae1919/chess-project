package com.chess.security;

import com.chess.ChessApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Logging out, stolen refresh tokens, changing the password and deleting the account.
 * Each test rolls back.
 */
@SpringBootTest(classes = ChessApplication.class)
@AutoConfigureMockMvc
@Transactional
@DisplayName("Sessions — logout, token reuse, password change, account deletion")
class AuthSessionTest {

    @Autowired MockMvc      mvc;
    @Autowired ObjectMapper json;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);

    private JsonNode register() throws Exception {
        var body = Map.of("username", "sess_" + suffix, "email", "sess-" + suffix + "@example.com",
                          "password", "secret123");
        return json.readTree(mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    @Test
    @DisplayName("logging out ends the session: its refresh token stops working")
    void logoutRevokesTheRefreshToken() throws Exception {
        String refreshToken = register().get("refreshToken").asText();

        mvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", refreshToken))))
            .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("logging out with a token that doesn't exist is harmless")
    void logoutWithUnknownTokenIsHarmless() throws Exception {
        mvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", "not-a-real-token"))))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("presenting a refresh token that was already used ends every session of that user")
    void reusedRefreshTokenRevokesEverySession() throws Exception {
        String first = register().get("refreshToken").asText();
        String second = json.readTree(refresh(first).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();

        // The first token was rotated away: whoever shows it now is replaying a stolen copy
        refresh(first).andExpect(status().isBadRequest());

        // ...so the session that rotated it is cut off too, in case the thief got there first
        refresh(second).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("changing the password ends the other sessions")
    void passwordChangeEndsOtherSessions() throws Exception {
        var tokens = register();

        mvc.perform(post("/api/users/me/change-password")
                .header("Authorization", "Bearer " + tokens.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("oldPassword", "secret123", "newPassword", "another456"))))
            .andExpect(status().isNoContent());

        refresh(tokens.get("refreshToken").asText()).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deleting the account needs the password again")
    void accountDeletionNeedsThePassword() throws Exception {
        String access = register().get("accessToken").asText();

        mvc.perform(delete("/api/users/me")
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("password", "wrong-password"))))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isOk());

        mvc.perform(delete("/api/users/me")
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("password", "secret123"))))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("deleting the account without a password is refused")
    void accountDeletionWithoutBodyIsRefused() throws Exception {
        String access = register().get("accessToken").asText();

        mvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a request with no login is 401, so the client knows to sign in again")
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a refresh token used as an access token is 401")
    void refreshTokenAsAccessTokenIsUnauthorized() throws Exception {
        String refreshToken = register().get("refreshToken").asText();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + refreshToken))
            .andExpect(status().isUnauthorized());
    }
}
