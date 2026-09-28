package com.chronobeat.dto.game;

import com.chronobeat.domain.Difficulty;
import com.chronobeat.domain.MusicGenre;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param market ISO market code (e.g. "US", "ES"), or null for international/all markets
 * @param genre null means "all genres"
 * @param yearFrom null means "no lower bound"
 * @param yearTo null means "no upper bound"
 * @param maxLives null falls back to the server default (see {@code chronobeat.game.default-max-lives"})
 * @param maxRounds null means unlimited rounds (game ends only when lives run out)
 */
public record GameSettingsRequest(
        String market,
        MusicGenre genre,
        Integer yearFrom,
        Integer yearTo,
        @NotNull Difficulty difficulty,
        @Min(1) Integer maxLives,
        @Min(1) Integer maxRounds) {}
