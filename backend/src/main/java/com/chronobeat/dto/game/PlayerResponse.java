package com.chronobeat.dto.game;

import java.util.UUID;

public record PlayerResponse(
        UUID id,
        String displayName,
        int playerOrder,
        int score,
        int correctAnswers,
        int incorrectAnswers,
        double accuracy,
        int currentStreak,
        int bestStreak,
        int livesRemaining,
        boolean eliminated,
        int timelineSize) {}
