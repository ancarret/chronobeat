-- Play styles (turn-based vs. shared songs), race-to-N games and online rooms.

ALTER TABLE games
    ADD COLUMN play_style           VARCHAR(20) NOT NULL DEFAULT 'TURN_BASED',
    -- "First to N cards wins"; NULL keeps the classic lives-based ending.
    ADD COLUMN target_timeline_size INTEGER,
    -- Per-round time limit in seconds; NULL means untimed (always set for online games).
    ADD COLUMN answer_seconds       INTEGER,
    -- Short human-typable code for online rooms; NULL for solo/local games.
    ADD COLUMN room_code            VARCHAR(8);

-- A code only needs to be unique among rooms still in play, so finished games release theirs.
CREATE UNIQUE INDEX uq_games_open_room_code ON games (room_code)
    WHERE room_code IS NOT NULL AND status <> 'FINISHED';

ALTER TABLE game_players
    -- SHA-256 of the per-game secret an online player uses to act; NULL on shared-device games.
    ADD COLUMN token_hash VARCHAR(64),
    ADD COLUMN is_host    BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE game_rounds
    -- Held while a shared-song round waits for the other players (status LOCKED).
    ADD COLUMN guessed_song_id UUID,
    ADD COLUMN guessed_year    INTEGER,
    ADD COLUMN locked_at       TIMESTAMPTZ,
    -- Recorded at resolution so a finished round can be replayed to every viewer.
    ADD COLUMN valid_positions VARCHAR(100),
    ADD COLUMN guess_correct   BOOLEAN;

CREATE INDEX idx_game_rounds_game_round_number ON game_rounds (game_id, round_number);
