package com.chess.api.controller;

import com.chess.ChessApplication;
import com.chess.api.dto.ShowcaseDto;
import com.chess.application.ShowcaseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The landing page asks for the analysis before anyone has logged in. */
@SpringBootTest(classes = ChessApplication.class)
@AutoConfigureMockMvc
@DisplayName("GET /api/showcase")
class ShowcaseControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ShowcaseService service;

    private static ShowcaseDto.Showcase sample() {
        var best = new ShowcaseDto.MoveRef("d6e5", "dxe5");
        var score = new ShowcaseDto.Score(161, null);
        return new ShowcaseDto.Showcase(
            "Légal's trap, Paris, about 1750",
            "start", new ShowcaseDto.MoveRef("f3e5", "Nxe5"), "fen",
            new ShowcaseDto.Search(best, score, 12, 2_119_709, 5_993, 353_579,
                List.of(new ShowcaseDto.Line(best, score, List.of("dxe5", "Qxg4"))),
                List.of(new ShowcaseDto.Iteration(1, 457, 92, new ShowcaseDto.MoveRef("g4d1", "Bxd1"), new ShowcaseDto.Score(18, null)))),
            "reply",
            new ShowcaseDto.Trap(new ShowcaseDto.MoveRef("g4d1", "Bxd1"), new ShowcaseDto.Score(null, 2),
                List.of("Bxf7+", "Ke7", "Nd5#"), 3, 326),
            "neural network evaluation", Instant.parse("2026-10-13T12:00:00Z"));
    }

    @Test
    @DisplayName("answers without a login, cacheable, with the engine's figures")
    void publicAndCached() throws Exception {
        when(service.showcase()).thenReturn(CompletableFuture.completedFuture(sample()));

        mvc.perform(get("/api/showcase"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("max-age=600")))
            .andExpect(jsonPath("$.playerMove.san").value("Nxe5"))
            .andExpect(jsonPath("$.engine.bestMove.san").value("dxe5"))
            .andExpect(jsonPath("$.engine.score.cp").value(161))
            .andExpect(jsonPath("$.engine.nodes").value(2_119_709))
            .andExpect(jsonPath("$.engine.iterations[0].bestMove.san").value("Bxd1"))
            .andExpect(jsonPath("$.trap.score.mateIn").value(2));
    }

    @Test
    @DisplayName("a failed search is 503 with a retry hint, never made-up figures")
    void failedSearch() throws Exception {
        when(service.showcase()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));

        mvc.perform(get("/api/showcase"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "5"))
            .andExpect(jsonPath("$.engine").doesNotExist());
    }
}
