package com.chronobeat.dto.game;

import com.chronobeat.domain.Difficulty;
import com.chronobeat.domain.MusicGenre;
import com.chronobeat.domain.PlayStyle;

public record GameSettingsResponse(
        String market,
        MusicGenre genre,
        Integer yearFrom,
        Integer yearTo,
        Difficulty difficulty,
        int maxLives,
        Integer maxRounds,
        PlayStyle playStyle,
        Integer targetTimelineSize,
        Integer answerSeconds) {}
