-- ─── Custom enum types ────────────────────────────────────────────────────────

CREATE TYPE player_rank      AS ENUM ('Bronze','Argent','Or','Platine','Diamant','Grand_Maitre');
CREATE TYPE board_theme      AS ENUM ('classic_wood','marble_green','slate','blue_night');
CREATE TYPE piece_style      AS ENUM ('standard','modern','minimalist');
CREATE TYPE subscription_plan AS ENUM ('Free','Silver','Gold','Platine');
CREATE TYPE game_mode        AS ENUM ('ai','local','online','saved');
CREATE TYPE game_status      AS ENUM ('waiting','active','paused','finished','aborted');
CREATE TYPE piece_color      AS ENUM ('white','black');
CREATE TYPE piece_type       AS ENUM ('king','queen','rook','bishop','knight','pawn');
CREATE TYPE time_control_type AS ENUM ('blitz','rapid','classical','unlimited');
CREATE TYPE game_end_reason  AS ENUM (
    'checkmate','stalemate','resignation','timeout',
    'draw-agreement','threefold-repetition','fifty-move-rule','insufficient-material');

-- ─── users ────────────────────────────────────────────────────────────────────

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   TEXT         NOT NULL,
    avatar_url      TEXT,
    country         VARCHAR(10)  NOT NULL DEFAULT 'FR',
    elo             INT          NOT NULL DEFAULT 1200,
    rank            player_rank  NOT NULL DEFAULT 'Bronze',
    -- Stats (denormalised for fast reads; updated on game end)
    games_played    INT          NOT NULL DEFAULT 0,
    wins            INT          NOT NULL DEFAULT 0,
    losses          INT          NOT NULL DEFAULT 0,
    draws           INT          NOT NULL DEFAULT 0,
    current_streak  INT          NOT NULL DEFAULT 0,
    best_streak     INT          NOT NULL DEFAULT 0,
    -- Subscription
    subscription_plan  subscription_plan NOT NULL DEFAULT 'Free',
    subscription_active BOOLEAN          NOT NULL DEFAULT TRUE,
    subscription_renews_at TIMESTAMPTZ,
    -- Timestamps
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email    ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_elo      ON users(elo DESC);

-- ─── user_preferences ─────────────────────────────────────────────────────────
-- 1-to-1 with users; separate table keeps users narrow.

CREATE TABLE user_preferences (
    user_id                UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    board_theme            board_theme  NOT NULL DEFAULT 'classic_wood',
    piece_style            piece_style  NOT NULL DEFAULT 'standard',
    language               VARCHAR(10)  NOT NULL DEFAULT 'fr',
    sound_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    animations_enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    notifications_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    confirm_moves          BOOLEAN      NOT NULL DEFAULT FALSE,
    show_legal_moves       BOOLEAN      NOT NULL DEFAULT TRUE,
    enable_undo            BOOLEAN      NOT NULL DEFAULT FALSE,
    real_time_analysis     BOOLEAN      NOT NULL DEFAULT FALSE
);

-- ─── elo_history ──────────────────────────────────────────────────────────────

CREATE TABLE elo_history (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    elo         INT          NOT NULL,
    recorded_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_elo_history_user ON elo_history(user_id, recorded_at DESC);

-- ─── achievement_definitions ──────────────────────────────────────────────────
-- Master list of all possible achievements (seeded once).

CREATE TABLE achievement_definitions (
    id          VARCHAR(50)  PRIMARY KEY,
    icon        VARCHAR(10)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT         NOT NULL,
    target      INT                       -- NULL = boolean (no progress bar)
);

-- ─── user_achievements ────────────────────────────────────────────────────────

CREATE TABLE user_achievements (
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_id VARCHAR(50) NOT NULL REFERENCES achievement_definitions(id),
    unlocked       BOOLEAN     NOT NULL DEFAULT FALSE,
    progress       INT         NOT NULL DEFAULT 0,
    unlocked_at    TIMESTAMPTZ,
    PRIMARY KEY (user_id, achievement_id)
);

-- ─── refresh_tokens ───────────────────────────────────────────────────────────

CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT         NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ─── games ────────────────────────────────────────────────────────────────────

CREATE TABLE games (
    id                    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    mode                  game_mode       NOT NULL,
    status                game_status     NOT NULL DEFAULT 'waiting',
    current_turn          piece_color     NOT NULL DEFAULT 'white',
    -- White player
    white_user_id         UUID            REFERENCES users(id) ON DELETE SET NULL,
    white_username        VARCHAR(50)     NOT NULL,
    white_elo             INT,
    white_time_remaining_ms BIGINT        NOT NULL DEFAULT 0,
    white_is_ai           BOOLEAN         NOT NULL DEFAULT FALSE,
    white_ai_difficulty INTEGER CHECK (white_ai_difficulty BETWEEN 1 AND 6),
    -- Black player
    black_user_id         UUID            REFERENCES users(id) ON DELETE SET NULL,
    black_username        VARCHAR(50)     NOT NULL,
    black_elo             INT,
    black_time_remaining_ms BIGINT        NOT NULL DEFAULT 0,
    black_is_ai           BOOLEAN         NOT NULL DEFAULT FALSE,
    black_ai_difficulty INTEGER CHECK (black_ai_difficulty BETWEEN 1 AND 6),
    -- Board state (FEN is compact; board squares are derived from FEN)
    current_fen           TEXT            NOT NULL
        DEFAULT 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1',
    half_move_clock       INT             NOT NULL DEFAULT 0,
    full_move_number      INT             NOT NULL DEFAULT 1,
    -- Castling rights (redundant with FEN but fast to query)
    white_kingside_castle  BOOLEAN        NOT NULL DEFAULT TRUE,
    white_queenside_castle BOOLEAN        NOT NULL DEFAULT TRUE,
    black_kingside_castle  BOOLEAN        NOT NULL DEFAULT TRUE,
    black_queenside_castle BOOLEAN        NOT NULL DEFAULT TRUE,
    -- En passant target square
    en_passant_row        INT,       -- 0-7
    en_passant_col        INT,       -- 0-7
    -- Time control
    time_control_type     time_control_type NOT NULL DEFAULT 'rapid',
    time_control_initial_ms  BIGINT       NOT NULL DEFAULT 600000,
    time_control_increment_ms BIGINT      NOT NULL DEFAULT 0,
    -- Result
    result_winner         piece_color,
    result_reason         game_end_reason,
    -- Opening
    opening               VARCHAR(100),
    -- Timestamps
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_games_white_user ON games(white_user_id);
CREATE INDEX idx_games_black_user ON games(black_user_id);
CREATE INDEX idx_games_status     ON games(status);
CREATE INDEX idx_games_created    ON games(created_at DESC);

-- ─── game_moves ───────────────────────────────────────────────────────────────

CREATE TABLE game_moves (
    id                  BIGSERIAL       PRIMARY KEY,
    game_id             UUID            NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    move_number         INT             NOT NULL,        -- 1-based full-move number
    color               piece_color     NOT NULL,        -- who played this half-move
    from_row            INT        NOT NULL,
    from_col            INT        NOT NULL,
    to_row              INT        NOT NULL,
    to_col              INT        NOT NULL,
    piece_type          piece_type      NOT NULL,
    piece_color         piece_color     NOT NULL,
    captured_type       piece_type,
    captured_color      piece_color,
    promotion           piece_type,
    is_en_passant       BOOLEAN         NOT NULL DEFAULT FALSE,
    is_castling         VARCHAR(10),                     -- 'kingside' | 'queenside' | NULL
    is_check            BOOLEAN         NOT NULL DEFAULT FALSE,
    is_checkmate        BOOLEAN         NOT NULL DEFAULT FALSE,
    algebraic_notation  VARCHAR(10)     NOT NULL,
    played_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_game_moves_game ON game_moves(game_id, move_number);

-- ─── captured_pieces ──────────────────────────────────────────────────────────
-- Tracks captured pieces per player per game (for the UI material count).

CREATE TABLE captured_pieces (
    game_id     UUID        NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    player_color piece_color NOT NULL,
    piece_type  piece_type  NOT NULL,
    piece_color piece_color NOT NULL,
    quantity    INT    NOT NULL DEFAULT 1,
    PRIMARY KEY (game_id, player_color, piece_type, piece_color)
);

-- ─── saved_games ──────────────────────────────────────────────────────────────

CREATE TABLE saved_games (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id         UUID         NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    opponent_name   VARCHAR(50)  NOT NULL,
    mode            game_mode    NOT NULL,
    turn_number     INT          NOT NULL,
    player_color    piece_color  NOT NULL,
    opening         VARCHAR(100),
    thumbnail_fen   TEXT,
    saved_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_saved_games_user ON saved_games(user_id, saved_at DESC);

-- ─── chat_messages ────────────────────────────────────────────────────────────

CREATE TABLE chat_messages (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id         UUID         NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    sender_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_username VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL CHECK (char_length(content) <= 500),
    sent_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_game ON chat_messages(game_id, sent_at ASC);

-- ─── Seed achievement definitions ─────────────────────────────────────────────

INSERT INTO achievement_definitions(id, icon, name, description, target) VALUES
    ('first_win',     '🏆', 'First Victory',   'Win your first game',          NULL),
    ('win_10',        '🥇', 'On a Roll',        'Win 10 games',                 10),
    ('win_100',       '🎖️',  'Century Club',     'Win 100 games',                100),
    ('streak_5',      '🔥', 'Hot Streak',       'Win 5 games in a row',         5),
    ('streak_10',     '⚡', 'On Fire',          'Win 10 games in a row',        10),
    ('elo_1500',      '📈', 'Rising Star',      'Reach 1500 ELO',              NULL),
    ('elo_2000',      '🌟', 'Expert',           'Reach 2000 ELO',              NULL),
    ('games_50',      '♟️',  'Dedicated Player', 'Play 50 games',                50),
    ('games_500',     '🎓', 'Chess Scholar',    'Play 500 games',              500),
    ('checkmate_queen','👑', 'Regicide',         'Win by capturing the queen first', NULL);
