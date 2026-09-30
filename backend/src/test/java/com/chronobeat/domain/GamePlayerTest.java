package com.chronobeat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GamePlayerTest {

    @Test
    void correctAnswerIncrementsScoreAndStreak() {
        GamePlayer player = new GamePlayer("Andres", 0, 3);

        player.recordCorrectAnswer(110);

        assertThat(player.getScore()).isEqualTo(110);
        assertThat(player.getCorrectAnswers()).isEqualTo(1);
        assertThat(player.getCurrentStreak()).isEqualTo(1);
        assertThat(player.getBestStreak()).isEqualTo(1);
        assertThat(player.getLivesRemaining()).isEqualTo(3);
    }

    @Test
    void incorrectAnswerResetsStreakAndCostsALife() {
        GamePlayer player = new GamePlayer("Andres", 0, 3);
        player.recordCorrectAnswer(100);
        player.recordCorrectAnswer(100);

        player.recordIncorrectAnswer();

        assertThat(player.getCurrentStreak()).isEqualTo(0);
        assertThat(player.getBestStreak()).isEqualTo(2);
        assertThat(player.getLivesRemaining()).isEqualTo(2);
        assertThat(player.getIncorrectAnswers()).isEqualTo(1);
    }

    @Test
    void livesNeverGoNegative() {
        GamePlayer player = new GamePlayer("Andres", 0, 1);

        player.recordIncorrectAnswer();
        player.recordIncorrectAnswer();

        assertThat(player.getLivesRemaining()).isZero();
        assertThat(player.isEliminated()).isTrue();
    }

    @Test
    void accuracyIsZeroWithNoRoundsPlayed() {
        GamePlayer player = new GamePlayer("Andres", 0, 3);
        assertThat(player.getAccuracy()).isEqualTo(0.0);
    }

    @Test
    void accuracyReflectsCorrectOverTotalRounds() {
        GamePlayer player = new GamePlayer("Andres", 0, 5);
        player.recordCorrectAnswer(100);
        player.recordCorrectAnswer(100);
        player.recordIncorrectAnswer();

        assertThat(player.getAccuracy()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void gainExtraLifeIncrementsLivesRemaining() {
        GamePlayer player = new GamePlayer("Andres", 0, 2);

        player.gainExtraLife();

        assertThat(player.getLivesRemaining()).isEqualTo(3);
    }

    @Test
    void gainExtraLifeCanReviveAnEliminatedPlayer() {
        GamePlayer player = new GamePlayer("Andres", 0, 1);
        player.recordIncorrectAnswer();
        assertThat(player.isEliminated()).isTrue();

        player.gainExtraLife();

        assertThat(player.isEliminated()).isFalse();
        assertThat(player.getLivesRemaining()).isEqualTo(1);
    }
}
