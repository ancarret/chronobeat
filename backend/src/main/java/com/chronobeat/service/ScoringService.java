package com.chronobeat.service;

import com.chronobeat.config.GameProperties;
import org.springframework.stereotype.Service;

/** Centralizes the points formula so it lives in exactly one place. */
@Service
public class ScoringService {

    private final GameProperties properties;

    public ScoringService(GameProperties properties) {
        this.properties = properties;
    }

    /**
     * @param streakLevelAfterThisAnswer the player's consecutive-correct count including this answer
     */
    public int calculatePoints(int streakLevelAfterThisAnswer) {
        int cappedStreak = Math.min(streakLevelAfterThisAnswer, properties.getMaxStreakBonusLevel());
        int streakBonus = cappedStreak * properties.getStreakBonusPerLevel();
        return properties.getBasePointsPerCorrect() + streakBonus;
    }
}
