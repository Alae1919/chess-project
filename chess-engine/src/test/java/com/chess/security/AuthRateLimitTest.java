package com.chess.security;

import com.chess.ChessApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Guessing passwords or mass-registering is slowed down, per address. */
@SpringBootTest(classes = ChessApplication.class, properties = "app.rate-limit.auth-per-minute=3")
@AutoConfigureMockMvc
@DisplayName("Auth endpoints — rate limit")
class AuthRateLimitTest {

    @Autowired MockMvc mvc;

    private static RequestPostProcessor from(String address) {
        return request -> { request.setRemoteAddr(address); return request; };
    }

    private ResultActions login(RequestPostProcessor from) throws Exception {
        return mvc.perform(post("/api/auth/login")
            .with(from)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\",\"password\":\"wrong-password\"}"));
    }

    private ResultActions register(RequestPostProcessor from, String name) throws Exception {
        return mvc.perform(post("/api/auth/register")
            .with(from)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + name + "\",\"email\":\"" + name + "@example.com\",\"password\":\"secret123\"}"));
    }

    @Test
    @DisplayName("after the limit, an address is told to wait")
    void tooManyLogins() throws Exception {
        var me = from("203.0.113.10");
        for (int i = 0; i < 3; i++) login(me).andExpect(status().isBadRequest()); // wrong password, but allowed

        login(me).andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.detail", containsString("seconds")));
    }

    @Test
    @DisplayName("another address is not affected")
    void otherAddressesAreFine() throws Exception {
        var noisy = from("203.0.113.20");
        for (int i = 0; i < 5; i++) login(noisy);

        login(from("203.0.113.21")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("logging in and registering are counted separately")
    void bucketsAreSeparate() throws Exception {
        var me = from("203.0.113.30");
        for (int i = 0; i < 4; i++) login(me);

        register(me, "rl_" + UUID.randomUUID().toString().substring(0, 8)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("other endpoints are not limited")
    void otherEndpointsAreNotLimited() throws Exception {
        var me = from("203.0.113.40");
        for (int i = 0; i < 6; i++) {
            mvc.perform(get("/api/users/me").with(me)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("behind the proxy the real client is counted, not the proxy")
    void proxyHeaderIsUsedFromAPrivateAddress() throws Exception {
        RequestPostProcessor viaProxy = request -> {
            request.setRemoteAddr("172.18.0.5");                 // the nginx container
            request.addHeader("X-Forwarded-For", "198.51.100.77"); // appended by nginx: the client
            return request;
        };
        for (int i = 0; i < 3; i++) login(viaProxy);

        login(viaProxy).andExpect(status().isTooManyRequests());

        // someone else behind the same proxy is a different client
        login(request -> {
            request.setRemoteAddr("172.18.0.5");
            request.addHeader("X-Forwarded-For", "198.51.100.78");
            return request;
        }).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a forged proxy header from a public address is ignored")
    void forgedHeaderIsIgnored() throws Exception {
        // The attacker rotates X-Forwarded-For to look like a new client each time
        for (int i = 0; i < 3; i++) {
            final int n = i;
            login(request -> {
                request.setRemoteAddr("192.0.2.50");
                request.addHeader("X-Forwarded-For", "198.51.100." + (100 + n));
                return request;
            });
        }

        login(request -> {
            request.setRemoteAddr("192.0.2.50");
            request.addHeader("X-Forwarded-For", "198.51.100.200");
            return request;
        }).andExpect(status().isTooManyRequests());
    }
}
