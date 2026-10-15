package com.chess.infrastructure.metrics;

import com.chess.infrastructure.metrics.RumRecorder.Timing;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RumRecorder: browser timings into metrics")
class RumRecorderTest {

    private static final String PHONE = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36";
    private static final String DESKTOP = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/126.0 Safari/537.36";

    private SimpleMeterRegistry registry;
    private RumRecorder recorder;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        recorder = new RumRecorder(registry);
    }

    private DistributionSummary summary(String name, String... tags) {
        return registry.find(name).tags(tags).summary();
    }

    @Test
    @DisplayName("records a vital by route and device, and an app timing by variant")
    void recordsKnownTimings() {
        int n = recorder.record("/game/:id", List.of(
            new Timing("LCP", 1800.0, null),
            new Timing("game.firstFrame", 900.0, "cold"),
            new Timing("CLS", 0.04, null)), PHONE);

        assertThat(n).isEqualTo(3);
        assertThat(summary("rum.vital", "name", "LCP", "route", "/game/:id", "device", "mobile").totalAmount()).isEqualTo(1800);
        assertThat(summary("rum.timing", "name", "game.firstFrame", "variant", "cold", "device", "mobile").count()).isEqualTo(1);
        assertThat(summary("rum.cls", "route", "/game/:id").totalAmount()).isEqualTo(0.04);
    }

    @Test
    @DisplayName("drops unknown names and impossible values, and keeps unknown routes and variants to one series each")
    void staysWithinItsLists() {
        int n = recorder.record("/game/1b9e4c7a-0000-4000-8000-000000000000", List.of(
            new Timing("made.up", 10.0, null),
            new Timing("TTFB", -1.0, null),
            new Timing("TTFB", Double.NaN, null),
            new Timing("INP", 500_000.0, null),
            new Timing("CLS", 50.0, null),
            new Timing("move.roundtrip", 120.0, "anything"),
            new Timing("INP", 80.0, null)), DESKTOP);

        assertThat(n).isEqualTo(2);
        assertThat(registry.find("rum.timing").tags("name", "made.up").summary()).isNull();
        assertThat(summary("rum.timing", "name", "move.roundtrip", "variant", "none", "device", "desktop").count()).isEqualTo(1);
        assertThat(summary("rum.vital", "name", "INP", "route", "other").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("takes at most a fixed number of timings from one report")
    void capsAReport() {
        List<Timing> many = new ArrayList<>();
        for (int i = 0; i < RumRecorder.MAX_TIMINGS + 20; i++) many.add(new Timing("move.roundtrip", 50.0, null));
        assertThat(recorder.record("/game/:id", many, null)).isEqualTo(RumRecorder.MAX_TIMINGS);
    }
}
