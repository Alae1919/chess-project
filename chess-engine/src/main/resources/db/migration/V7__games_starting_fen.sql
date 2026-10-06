-- The position a game began from, so a reload can replay its moves; null = the standard start
ALTER TABLE games ADD COLUMN starting_fen TEXT;
