package com.chronobeat.dto.game;

import com.chronobeat.domain.Difficulty;
import com.chronobeat.domain.MusicGenre;
import com.chronobeat.domain.PlayStyle;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param market ISO market code (e.g. "US", "ES"), or null for international/all markets
 * @param genre null means "all genres"
 * @param yearFrom null means "no lower bound"
 * @param yearTo null means "no upper bound"
 * @param maxLives null falls back to the server default (see {@code chronobeat.game.default-max-lives"})
 * @param maxRounds null means unlimited rounds (game ends only when lives run out)
 * @param playStyle null means turn-based; {@code SHARED_SONGS} deals every player the same song each round
 * @param targetTimelineSize when set, the first player to build a timeline of this many cards wins
 * @param answerSeconds per-round time limit; null means untimed (online games always get one)
 */
public record GameSettingsRequest(
        String market,
        MusicGenre genre,
        Integer yearFrom,
        Integer yearTo,
        @NotNull Difficulty difficulty,
        @Min(1) Integer maxLives,
        @Min(1) Integer maxRounds,
        PlayStyle playStyle,
        @Min(2) @Max(100) Integer targetTimelineSize,
        @Min(10) @Max(300) Integer answerSeconds) {

    /** Classic rules: turn-based, no race, untimed. */
    public GameSettingsRequest(
            String market, MusicGenre genre, Integer yearFrom, Integer yearTo, Difficulty difficulty, Integer maxLives, Integer maxRounds) {
        this(market, genre, yearFrom, yearTo, difficulty, maxLives, maxRounds, null, null, null);
    }
}
