package com.chess.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot of the stable, DB-only fields for a game.
 *
 * Set once by GamePersistenceService after the game row is persisted, then
 * cached inside GameSession so subsequent toFullGameDto calls can serve the
 * response from memory without hitting the database.
 *
 * Time remaining is intentionally excluded — it changes every move and
 * is tracked as mutable state directly on GameSession.
 */
public record GameMetadata(

    String  mode,

    // ── White player ──────────────────────────────────────────────────────────
    String  whiteUsername,
    UUID    whiteUserId,
    Integer whiteElo,
    boolean whiteIsAi,
    Integer whiteAiDifficulty,

    // ── Black player ──────────────────────────────────────────────────────────
    String  blackUsername,
    UUID    blackUserId,
    Integer blackElo,
    boolean blackIsAi,
    Integer blackAiDifficulty,

    // ── Time control ──────────────────────────────────────────────────────────
    String timeControlType,
    long   timeControlInitialMs,
    long   timeControlIncrementMs,

    // ── Timestamps ────────────────────────────────────────────────────────────
    Instant createdAt

) {}
