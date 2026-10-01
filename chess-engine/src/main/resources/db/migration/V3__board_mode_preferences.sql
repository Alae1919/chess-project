-- V3: board mode (3D / 2D) and the style chosen for each, per user.
-- Plain VARCHAR + CHECK rather than Postgres enums so a new style is a one-line constraint change.

ALTER TABLE user_preferences
    ADD COLUMN board_mode     VARCHAR(2)  NOT NULL DEFAULT '3d',
    ADD COLUMN board_style_3d VARCHAR(20) NOT NULL DEFAULT 'marble-gold',
    ADD COLUMN board_style_2d VARCHAR(20) NOT NULL DEFAULT 'classic-wood',
    ADD CONSTRAINT chk_board_mode     CHECK (board_mode IN ('2d', '3d')),
    ADD CONSTRAINT chk_board_style_3d CHECK (board_style_3d IN ('marble-gold', 'classic-wood', 'ebony-ivory')),
    ADD CONSTRAINT chk_board_style_2d CHECK (board_style_2d IN ('classic-wood', 'luxe', 'slate-blue'));
