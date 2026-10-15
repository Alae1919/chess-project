package com.chess.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EngineCpuGate: one search per core")
class EngineCpuGateTest {

    @Test
    @DisplayName("runs a search when a core is free, and frees it afterwards, even if the search throws")
    void runsAndReleases() {
        var gate = new EngineCpuGate(1);
        assertEquals(42, gate.run("ai", () -> 42));
        assertThrows(IllegalArgumentException.class, () -> gate.run("ai", () -> { throw new IllegalArgumentException(); }));
        assertEquals(7, gate.tryRun("analysis", Duration.ofMillis(10), () -> 7));
    }

    @Test
    @DisplayName("turns an analysis away when every core stays busy, without waiting longer than it was told")
    void turnsAwayWhenBusy() throws Exception {
        var gate = new EngineCpuGate(1);
        var holding = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var searcher = new Thread(() -> gate.run("ai", () -> {
            holding.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            return null;
        }));
        searcher.start();
        assertTrue(holding.await(5, TimeUnit.SECONDS));

        long start = System.nanoTime();
        assertThrows(IllegalStateException.class, () -> gate.tryRun("analysis", Duration.ofMillis(100), () -> 1));
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms >= 90 && ms < 2_000, "waited " + ms + " ms");

        release.countDown();
        searcher.join(5_000);
        assertEquals(1, gate.tryRun("analysis", Duration.ofMillis(100), () -> 1));
    }
}
