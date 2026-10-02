-- Rating change each player got from a finished online game (null for unrated games)
ALTER TABLE games
    ADD COLUMN white_elo_change INT,
    ADD COLUMN black_elo_change INT;
