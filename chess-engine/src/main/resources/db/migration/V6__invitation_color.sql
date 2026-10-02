-- The colour the inviter asked to play (a rematch swaps colours); null = decided at random
ALTER TABLE game_invitations ADD COLUMN inviter_color VARCHAR(5);
