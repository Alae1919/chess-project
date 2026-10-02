package com.chess.security;

import com.chess.ChessApplication;
import com.chess.persistence.entity.UserEntity;
import com.chess.persistence.entity.UserPreferencesEntity;
import com.chess.persistence.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * How bearer tokens map to users. Runs against the real security chain and
 * database; each test rolls back.
 */
@SpringBootTest(classes = ChessApplication.class)
@AutoConfigureMockMvc
@Transactional
@DisplayName("Authentication — tokens resolve to the right user")
class AuthSecurityTest {

    @Autowired MockMvc        mvc;
    @Autowired ObjectMapper   json;
    @Autowired JwtService     jwtService;
    @Autowired UserRepository userRepository;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);

    private JsonNode register(String username, String email) throws Exception {
        var body = Map.of("username", username, "email", email, "password", "secret123");
        String response = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    @Test
    @DisplayName("registering with an email as the username is rejected")
    void usernameThatLooksLikeAnEmailIsRejected() throws Exception {
        var body = Map.of("username", "victim@example.com",
                          "email", "attacker-" + suffix + "@example.com",
                          "password", "secret123");
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a user named after someone else's email is still only themselves")
    void tokenNeverResolvesToTheUserWhoseEmailMatchesTheUsername() throws Exception {
        String victimEmail = "victim-" + suffix + "@example.com";
        register("victim_" + suffix, victimEmail);

        // A row written before usernames were restricted: username = the victim's email
        var attacker = new UserEntity();
        attacker.setUsername(victimEmail);
        attacker.setEmail("attacker-" + suffix + "@example.com");
        attacker.setPasswordHash("unused");
        attacker.setPreferences(UserPreferencesEntity.defaultsFor(attacker));
        attacker = userRepository.save(attacker);

        String token = jwtService.generateAccessToken(attacker.getId(), attacker.getUsername());

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(attacker.getId().toString()))
            .andExpect(jsonPath("$.email").value(attacker.getEmail()));
    }

    @Test
    @DisplayName("a refresh token is not accepted as an access token")
    void refreshTokenCannotCallTheApi() throws Exception {
        String refreshToken = register("player_" + suffix, "player-" + suffix + "@example.com")
            .get("refreshToken").asText();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + refreshToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the access token keeps working after a rename")
    void renameKeepsTheTokenValid() throws Exception {
        String token = register("before_" + suffix, "rename-" + suffix + "@example.com")
            .get("accessToken").asText();

        mvc.perform(patch("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "after_" + suffix))))
            .andExpect(status().isOk());

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("after_" + suffix));
    }

    @Test
    @DisplayName("renaming to a taken username is rejected")
    void renameToTakenUsernameIsRejected() throws Exception {
        register("taken_" + suffix, "first-" + suffix + "@example.com");
        String token = register("other_" + suffix, "second-" + suffix + "@example.com")
            .get("accessToken").asText();

        mvc.perform(patch("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "taken_" + suffix))))
            .andExpect(status().isBadRequest());
    }
}
