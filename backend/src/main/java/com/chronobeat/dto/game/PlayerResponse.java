package com.chronobeat.dto.game;

import java.util.UUID;

/**
 * @param host the player who created an online room and starts the game
 * @param answered whether this player has locked in an answer for the round in progress
 *     (only ever true while a shared-song round is waiting on the others)
 */
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
        int timelineSize,
        boolean host,
        boolean answered) {}
