package com.chess.api.controller;

import com.chess.api.dto.ShowcaseDto;
import com.chess.application.ShowcaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** The landing page's engine analysis. Public: it is the same fixed position for everyone. */
@RestController
@RequestMapping("/api/showcase")
@Tag(name = "Showcase", description = "The engine's analysis of the landing page's position")
public class ShowcaseController {

    /** How long a request waits for a search that is still running before asking to come back */
    static final long WAIT_SECONDS = 20;

    private final ShowcaseService showcase;

    public ShowcaseController(ShowcaseService showcase) {
        this.showcase = showcase;
    }

    @GetMapping
    @Operation(summary = "The engine's analysis of the landing page's position (searched once, then cached)")
    public ResponseEntity<?> get() {
        try {
            ShowcaseDto.Showcase result = showcase.showcase().get(WAIT_SECONDS, TimeUnit.SECONDS);
            return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .body(result);
        } catch (TimeoutException e) {
            return unavailable("The engine is still thinking; try again in a moment");
        } catch (ExecutionException e) {
            return unavailable("The analysis is not available right now");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable("The analysis is not available right now");
        }
    }

    private static ResponseEntity<Map<String, String>> unavailable(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, "5")
            .cacheControl(CacheControl.noStore())
            .body(Map.of("message", message));
    }
}
