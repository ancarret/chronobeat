-- Anonymous player profiles: identity without accounts.
--
-- A profile is created on demand from the browser. The server hands out a random
-- secret token exactly once and only ever stores its SHA-256 hash, so a database
-- leak can't be replayed and knowing a profile's (public) id is not enough to
-- act as it. The token is high-entropy, hence a fast hash is appropriate here.

CREATE TABLE profiles (
    id          UUID PRIMARY KEY,
    nickname    VARCHAR(40)  NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_profiles_token_hash UNIQUE (token_hash)
);

-- A game player may belong to a profile; guest players (friends typed in on a shared
-- device) stay unlinked. Deleting a profile keeps the game history, just anonymised.
ALTER TABLE game_players
    ADD COLUMN profile_id UUID REFERENCES profiles (id) ON DELETE SET NULL;

CREATE INDEX idx_game_players_profile_id ON game_players (profile_id);
