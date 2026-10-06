package com.chess.security;

import com.chess.ChessApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The container healthcheck needs the health probe without a login, and nothing else of the actuator. */
@SpringBootTest(classes = ChessApplication.class)
@AutoConfigureMockMvc
@DisplayName("Actuator — only the health probe is reachable")
class HealthEndpointTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("health is UP without a login, and shows no details")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("the readiness probe is UP")
    void readiness() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("no other actuator endpoint is reachable")
    void nothingElseIsExposed() throws Exception {
        for (String endpoint : new String[] {"env", "beans", "heapdump", "metrics", "mappings"}) {
            mvc.perform(get("/actuator/" + endpoint))
                .andExpect(status().is4xxClientError());
        }
    }
}
