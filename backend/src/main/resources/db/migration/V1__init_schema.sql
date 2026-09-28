-- Chronobeat initial schema.
-- UUID primary keys are generated client-side by Hibernate, so no DB extension
-- (pgcrypto/uuid-ossp) is required for gen_random_uuid()/uuid_generate_v4().

CREATE TABLE songs (
    id                      UUID PRIMARY KEY,
    provider                VARCHAR(30)  NOT NULL,
    external_id             VARCHAR(100) NOT NULL,
    title                   VARCHAR(500) NOT NULL,
    artist                  VARCHAR(500) NOT NULL,
    album                   VARCHAR(500),
    release_date            DATE,
    release_year            INTEGER      NOT NULL,
    canonical_release_year  INTEGER,
    genre                   VARCHAR(30)  NOT NULL,
    raw_genre               VARCHAR(100),
    market                  VARCHAR(10),
    artwork_url             VARCHAR(500),
    preview_url             VARCHAR(500),
    duration_millis         INTEGER,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_songs_provider_external_id UNIQUE (provider, external_id)
);

CREATE INDEX idx_songs_market ON songs (market);
CREATE INDEX idx_songs_genre ON songs (genre);
CREATE INDEX idx_songs_release_year ON songs (release_year);
CREATE INDEX idx_songs_artist ON songs (artist);

CREATE TABLE games (
    id                      UUID PRIMARY KEY,
    mode                    VARCHAR(20) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    market                  VARCHAR(10),
    genre                   VARCHAR(30),
    year_from               INTEGER,
    year_to                 INTEGER,
    difficulty              VARCHAR(10) NOT NULL,
    max_lives               INTEGER     NOT NULL,
    max_rounds              INTEGER,
    current_round_number    INTEGER     NOT NULL DEFAULT 0,
    version                 BIGINT      NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_games_status ON games (status);

CREATE TABLE game_players (
    id                  UUID PRIMARY KEY,
    game_id             UUID NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    display_name        VARCHAR(60) NOT NULL,
    player_order        INTEGER     NOT NULL,
    score               INTEGER     NOT NULL DEFAULT 0,
    correct_answers     INTEGER     NOT NULL DEFAULT 0,
    incorrect_answers   INTEGER     NOT NULL DEFAULT 0,
    current_streak      INTEGER     NOT NULL DEFAULT 0,
    best_streak         INTEGER     NOT NULL DEFAULT 0,
    lives_remaining     INTEGER     NOT NULL,
    CONSTRAINT uq_game_players_game_order UNIQUE (game_id, player_order)
);

CREATE INDEX idx_game_players_game_id ON game_players (game_id);

CREATE TABLE timeline_entries (
    id                  UUID PRIMARY KEY,
    game_player_id      UUID NOT NULL REFERENCES game_players (id) ON DELETE CASCADE,
    song_id             UUID NOT NULL REFERENCES songs (id),
    position_index      INTEGER NOT NULL,
    added_at_round      INTEGER NOT NULL,
    -- DEFERRABLE: inserting a new entry shifts existing positions up in the same
    -- transaction, and Hibernate flushes inserts before updates, so the (player,
    -- position) pairs are transiently non-unique until commit. Checking at commit
    -- time (instead of per-statement) lets that happen without an artificial
    -- two-phase position shuffle in application code.
    CONSTRAINT uq_timeline_entries_player_position UNIQUE (game_player_id, position_index) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_timeline_entries_game_player_id ON timeline_entries (game_player_id);

CREATE TABLE game_rounds (
    id                  UUID PRIMARY KEY,
    game_id             UUID NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    game_player_id      UUID NOT NULL REFERENCES game_players (id) ON DELETE CASCADE,
    song_id             UUID NOT NULL REFERENCES songs (id),
    round_number        INTEGER     NOT NULL,
    is_anchor_round     BOOLEAN     NOT NULL DEFAULT FALSE,
    status              VARCHAR(20) NOT NULL,
    submitted_position  INTEGER,
    correct             BOOLEAN,
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL,
    resolved_at         TIMESTAMPTZ
);

CREATE INDEX idx_game_rounds_game_id_status ON game_rounds (game_id, status);
CREATE INDEX idx_game_rounds_game_player_id ON game_rounds (game_player_id);
