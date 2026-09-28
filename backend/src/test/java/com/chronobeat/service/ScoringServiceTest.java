package com.chronobeat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chronobeat.config.GameProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScoringServiceTest {

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        GameProperties properties = new GameProperties();
        properties.setBasePointsPerCorrect(100);
        properties.setStreakBonusPerLevel(10);
        properties.setMaxStreakBonusLevel(5);
        scoringService = new ScoringService(properties);
    }

    @Test
    void firstCorrectAnswerGetsBasePointsPlusOneStreakLevel() {
        assertThat(scoringService.calculatePoints(1)).isEqualTo(110);
    }

    @Test
    void streakBonusGrowsLinearlyWithConsecutiveCorrectAnswers() {
        assertThat(scoringService.calculatePoints(3)).isEqualTo(130);
    }

    @Test
    void streakBonusIsCappedAtConfiguredMaxLevel() {
        assertThat(scoringService.calculatePoints(5)).isEqualTo(150);
        assertThat(scoringService.calculatePoints(20)).isEqualTo(150);
    }
}
