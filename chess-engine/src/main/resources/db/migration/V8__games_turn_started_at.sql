-- When the side to move's clock started, so a restart can charge the time the server was down
ALTER TABLE games ADD COLUMN turn_started_at TIMESTAMPTZ;
