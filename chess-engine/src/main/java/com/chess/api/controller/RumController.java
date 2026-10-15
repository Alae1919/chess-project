package com.chess.api.controller;

import com.chess.infrastructure.metrics.RumRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Timings measured in the browser: Web Vitals of each page, and the app's own (a move's round
 * trip, the 3D board's first frame). Public, since pages are timed before anyone logs in.
 *
 * The page sends them with {@code navigator.sendBeacon} as it is hidden, which posts the JSON as
 * text/plain (a type that needs no CORS preflight), so the body is read as a string.
 */
@RestController
@RequestMapping("/api/rum")
@Tag(name = "Telemetry", description = "Timings measured in the browser")
public class RumController {

    /** What a page sends: its route as the router declares it (/game/:id, not the game's id), and its timings. */
    public record Report(String route, List<RumRecorder.Timing> timings) { }

    private final RumRecorder recorder;
    private final ObjectMapper objectMapper;

    public RumController(RumRecorder recorder, ObjectMapper objectMapper) {
        this.recorder = recorder;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Operation(summary = "Record timings measured in the browser")
    public ResponseEntity<Void> record(@RequestBody String body,
                                       @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        Report report;
        try {
            report = objectMapper.readValue(body, Report.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }
        recorder.record(report.route(), report.timings(), userAgent);
        return ResponseEntity.noContent().build();
    }
}
