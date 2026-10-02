-- A player who stays disconnected from an online game loses it
ALTER TYPE game_end_reason ADD VALUE 'abandonment';
