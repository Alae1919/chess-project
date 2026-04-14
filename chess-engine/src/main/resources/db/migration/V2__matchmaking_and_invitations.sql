-- V2: Online multiplayer — matchmaking queue and friend invitations

CREATE TYPE invitation_status AS ENUM ('pending', 'accepted', 'declined', 'expired', 'cancelled');

-- One row per player waiting for a random opponent
CREATE TABLE matchmaking_queue (
    id                        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    username                  VARCHAR(50)     NOT NULL,
    elo                       INT             NOT NULL,
    time_control_type         time_control_type NOT NULL,
    time_control_initial_ms   BIGINT          NOT NULL,
    time_control_increment_ms BIGINT          NOT NULL DEFAULT 0,
    joined_at                 TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    matched                   BOOLEAN         NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_queue_user UNIQUE (user_id)
);

CREATE INDEX idx_queue_tc  ON matchmaking_queue (time_control_type, matched, joined_at);
CREATE INDEX idx_queue_elo ON matchmaking_queue (elo) WHERE matched = FALSE;

-- Friend challenge invitations
CREATE TABLE game_invitations (
    id                        UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    inviter_id                UUID              NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    inviter_username          VARCHAR(50)       NOT NULL,
    invitee_id                UUID              NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_username          VARCHAR(50)       NOT NULL,
    time_control_type         time_control_type NOT NULL,
    time_control_initial_ms   BIGINT            NOT NULL,
    time_control_increment_ms BIGINT            NOT NULL DEFAULT 0,
    status                    invitation_status NOT NULL DEFAULT 'pending',
    game_id                   UUID              REFERENCES games(id) ON DELETE SET NULL,
    created_at                TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    expires_at                TIMESTAMPTZ       NOT NULL DEFAULT (NOW() + INTERVAL '2 minutes')
);

CREATE INDEX idx_inv_invitee ON game_invitations (invitee_id, status);
CREATE INDEX idx_inv_inviter ON game_invitations (inviter_id, status);
