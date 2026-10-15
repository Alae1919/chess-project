package com.chess.infrastructure.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * The application's own timers, on Micrometer's global registry. Spring Boot adds its Prometheus
 * registry to that one, so the meters show on /actuator/prometheus; classes built without Spring
 * (the engine, tests) record into a registry that keeps nothing.
 *
 * Every latency meter publishes the same few histogram buckets rather than a full percentile
 * histogram: Grafana gets its quantiles from them, and the series stay few enough for a free tier.
 */
public final class ChessMeters {

    private ChessMeters() { }

    /** Histogram buckets for anything measured in milliseconds, from a socket send to a long search. */
    static final Duration[] LATENCY_BUCKETS = {
        Duration.ofMillis(5), Duration.ofMillis(10), Duration.ofMillis(25), Duration.ofMillis(50),
        Duration.ofMillis(100), Duration.ofMillis(250), Duration.ofMillis(500),
        Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5),
    };

    /** The same buckets as numbers of milliseconds, for values the browser reports (see {@link #summary}). */
    static final double[] MS_BUCKETS = {
        50, 100, 200, 300, 500, 800, 1000, 1500, 2000, 2500, 3000, 4000, 6000, 10000,
    };

    /** A timer with the shared latency buckets. {@code tags} are key, value pairs. */
    public static Timer timer(String name, String description, String... tags) {
        return Timer.builder(name)
            .description(description)
            .tags(tags)
            .serviceLevelObjectives(LATENCY_BUCKETS)
            .register(Metrics.globalRegistry);
    }

    /** Runs {@code work} and records how long it took, whether it returned or threw. */
    public static <T> T time(Timer timer, Supplier<T> work) {
        long start = System.nanoTime();
        try {
            return work.get();
        } finally {
            timer.record(Duration.ofNanos(System.nanoTime() - start));
        }
    }

    /** A distribution of values already in milliseconds (or another unit, with its own buckets). */
    public static DistributionSummary summary(String name, String description, String baseUnit,
                                              double[] buckets, String... tags) {
        return DistributionSummary.builder(name)
            .description(description)
            .baseUnit(baseUnit)
            .tags(tags)
            .serviceLevelObjectives(buckets)
            .register(Metrics.globalRegistry);
    }

    public static DistributionSummary millisSummary(String name, String description, String... tags) {
        return summary(name, description, "milliseconds", MS_BUCKETS, tags);
    }
}
