package com.chess.persistence.entity;

/**
 * Enums whose constant names match PostgreSQL enum labels in Flyway migrations.
 */
public final class DatabaseEnums {

    private DatabaseEnums() {}

    public enum GameEndReason {
        checkmate, stalemate, resignation, timeout,
        draw_agreement, threefold_repetition, fifty_move_rule, insufficient_material
    }

    /** {@code piece_color} */
    public enum PlayerSide {
        white,
        black
    }

    /** {@code piece_type} */
    public enum PieceKind {
        king,
        queen,
        rook,
        bishop,
        knight,
        pawn
    }

    /** {@code game_mode} */
    public enum GameMode {
        ai,
        local,
        online,
        saved
    }

    /** {@code game_status} */
    public enum GameStatus {
        waiting,
        active,
        paused,
        finished,
        aborted
    }

    /** {@code time_control_type} */
    public enum TimeControlKind {
        blitz,
        rapid,
        classical,
        unlimited
    }

    /** {@code invitation_status} */
    public enum InvitationStatus {
        pending,
        accepted,
        declined,
        expired,
        cancelled
    }
}
