package com.chess.api.controller;

import com.chess.ChessApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pages post their timings with sendBeacon, before anyone has logged in; the scrape reads them back. */
@SpringBootTest(classes = ChessApplication.class)
@AutoConfigureMockMvc
// Spring Boot leaves metrics export off in tests; this test reads the scrape
@AutoConfigureObservability(tracing = false)
@DisplayName("POST /api/rum and the metrics scrape")
class RumControllerTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("accepts a beacon without a login, and the timing shows on /actuator/prometheus")
    void beaconReachesTheScrape() throws Exception {
        String body = "{\"route\":\"/login\",\"timings\":[{\"name\":\"LCP\",\"value\":1234}]}";
        mvc.perform(post("/api/rum").contentType(MediaType.TEXT_PLAIN).content(body))
            .andExpect(status().isNoContent())
            .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")));

        mvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("rum_vital_milliseconds_count{application=\"rexchess\",device=\"desktop\",name=\"LCP\",route=\"/login\"")));
    }

    @Test
    @DisplayName("refuses a body that is not JSON")
    void refusesGarbage() throws Exception {
        mvc.perform(post("/api/rum").contentType(MediaType.TEXT_PLAIN).content("not json"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("keeps a request id that nginx sent")
    void keepsTheProxysRequestId() throws Exception {
        mvc.perform(post("/api/rum").header("X-Request-Id", "0123456789abcdef0123456789abcdef")
                .contentType(MediaType.TEXT_PLAIN).content("{\"timings\":[]}"))
            .andExpect(header().string("X-Request-Id", "0123456789abcdef0123456789abcdef"));
    }
}
