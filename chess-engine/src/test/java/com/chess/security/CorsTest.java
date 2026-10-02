package com.chess.security;

import com.chess.ChessApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Browsers may only call the API from allowed origins (defaults: localhost on any port). */
@SpringBootTest(classes = ChessApplication.class)
@AutoConfigureMockMvc
@DisplayName("CORS — only allowed origins")
class CorsTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("the dev frontend's origin is allowed")
    void allowedOrigin() throws Exception {
        mvc.perform(options("/api/auth/login")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    @DisplayName("any other site is refused")
    void otherOriginIsRefused() throws Exception {
        mvc.perform(options("/api/auth/login")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
