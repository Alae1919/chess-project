-- Indexes for the queries that run often or scan whole tables, and two that only cost writes.
-- The tables are small, so a plain CREATE INDEX inside Flyway's transaction is quick.

-- Match history and saved games: one player's games of one status, newest first
-- (GameRepository.findMatchHistoryForUserWithStatus, findActiveGamesForUserWithStatuses).
-- They lead with the player, so they also serve everything the one-column indexes did.
CREATE INDEX idx_games_white_status_updated ON games (white_user_id, status, updated_at DESC);
CREATE INDEX idx_games_black_status_updated ON games (black_user_id, status, updated_at DESC);
DROP INDEX idx_games_white_user;
DROP INDEX idx_games_black_user;

-- Searching players by the start of their name, whatever the case (UserRepository.searchByUsernamePrefix:
-- LOWER(username) LIKE 'ab%'), which read the whole table
CREATE INDEX idx_users_username_lower ON users (lower(username) text_pattern_ops);

-- The UNIQUE constraints on email and username come with indexes of their own: these were second copies,
-- kept up to date on every write for nothing
DROP INDEX idx_users_email;
DROP INDEX idx_users_username;

-- Expired invitations are looked for every 30 s (InvitationService, findByStatusAndExpiresAtBefore)
CREATE INDEX idx_inv_status_expires ON game_invitations (status, expires_at);

-- Foreign keys with no index: deleting a game or a user scanned these tables to update or delete the rows
-- pointing at it (ON DELETE SET NULL / CASCADE)
CREATE INDEX idx_inv_game ON game_invitations (game_id);
CREATE INDEX idx_chat_sender ON chat_messages (sender_id);
