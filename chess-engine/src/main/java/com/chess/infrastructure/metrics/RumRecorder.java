package com.chess.infrastructure.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the timings browsers send (see the frontend's TelemetryService) into metrics.
 *
 * Anyone can post them, so nothing is taken as given: names, routes and variants must be on the
 * lists below (anything else is dropped, or counted as "other"), values must be plausible, and
 * nothing that identifies a visitor is kept: no user, no address, no full user agent, only
 * whether it was a phone. That also keeps the number of series small and fixed.
 */
@Component
public class RumRecorder {

    /** The most timings one request may carry; a page sends a few at a time. */
    public static final int MAX_TIMINGS = 50;
    /** Longer than any page load or move worth measuring: past this it is a tab left asleep. */
    static final double MAX_MS = 120_000;

    /** Core Web Vitals and their neighbours, in ms, recorded per route. */
    static final Set<String> VITALS = Set.of("LCP", "INP", "TTFB", "FCP");
    /** The app's own timings, in ms; the game is their route, so they are recorded per variant instead. */
    static final Map<String, Set<String>> TIMINGS = Map.of(
        "move.roundtrip", Set.of(),
        "ai.reply",       Set.of(),
        "legal.select",   Set.of(),
        "game.start",     Set.of(),
        "game.firstFrame", Set.of("cold", "warm"),
        "desk.assets",    Set.of("cold", "warm"),
        "ws.reconnect",   Set.of("game", "lobby"));
    /** The app's routes, as the router declares them (see app.routes.ts). */
    static final Set<String> ROUTES = Set.of(
        "/", "/home", "/login", "/register", "/game", "/game/:id", "/account", "/online");

    /** Layout shift is a score, mostly between 0 and 1. */
    private static final double[] CLS_BUCKETS = {0.01, 0.05, 0.1, 0.15, 0.25, 0.5, 1};

    public record Timing(String name, Double value, String variant) { }

    private final MeterRegistry registry;

    public RumRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    /** @return how many of the timings were recorded */
    public int record(String route, List<Timing> timings, String userAgent) {
        if (timings == null) return 0;
        String page = route != null && ROUTES.contains(route) ? route : "other";
        String device = userAgent != null && userAgent.contains("Mobi") ? "mobile" : "desktop";
        int recorded = 0;
        for (Timing t : timings.subList(0, Math.min(timings.size(), MAX_TIMINGS))) {
            if (t == null || t.name() == null || t.value() == null) continue;
            double v = t.value();
            if (!(v >= 0)) continue; // also NaN
            DistributionSummary summary = summaryFor(t, page, device);
            if (summary == null) continue;
            if (!"CLS".equals(t.name()) && v > MAX_MS) continue;
            summary.record(v);
            recorded++;
        }
        return recorded;
    }

    private DistributionSummary summaryFor(Timing t, String page, String device) {
        String name = t.name();
        if ("CLS".equals(name)) {
            if (t.value() > 10) return null;
            return DistributionSummary.builder("rum.cls")
                .description("Cumulative layout shift of a page view, as the browser measured it")
                .tags("route", page, "device", device)
                .serviceLevelObjectives(CLS_BUCKETS)
                .register(registry);
        }
        if (VITALS.contains(name)) {
            return DistributionSummary.builder("rum.vital")
                .description("A Web Vital of a page view, as the browser measured it")
                .baseUnit("milliseconds")
                .tags("name", name, "route", page, "device", device)
                .serviceLevelObjectives(ChessMeters.MS_BUCKETS)
                .register(registry);
        }
        Set<String> variants = TIMINGS.get(name);
        if (variants == null) return null;
        String variant = t.variant() != null && variants.contains(t.variant()) ? t.variant() : "none";
        return DistributionSummary.builder("rum.timing")
            .description("A timing the app measured in the browser: a move's round trip, the 3D board's first frame...")
            .baseUnit("milliseconds")
            .tags("name", name, "variant", variant, "device", device)
            .serviceLevelObjectives(ChessMeters.MS_BUCKETS)
            .register(registry);
    }
}
