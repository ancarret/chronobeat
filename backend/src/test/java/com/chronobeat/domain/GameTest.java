package com.chronobeat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GameTest {

    private Game newGame(int maxLives, Integer maxRounds) {
        GameSettings settings = new GameSettings(null, null, null, null, Difficulty.NORMAL, maxLives, maxRounds);
        return new Game(GameMode.SOLO, settings);
    }

    @Test
    void cannotStartTwice() {
        Game game = newGame(3, null);
        game.start();
        assertThatThrownBy(game::start).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void finishesWhenAllPlayersAreEliminated() {
        Game game = newGame(1, null);
        GamePlayer player = new GamePlayer("Solo", 0, 1);
        game.addPlayer(player);

        assertThat(game.shouldFinish()).isFalse();
        player.recordIncorrectAnswer();
        assertThat(game.shouldFinish()).isTrue();
    }

    @Test
    void finishesWhenRoundCapIsReachedRegardlessOfLives() {
        Game game = newGame(10, 2);
        game.addPlayer(new GamePlayer("Solo", 0, 10));

        game.incrementRoundNumber();
        assertThat(game.shouldFinish()).isFalse();
        game.incrementRoundNumber();
        assertThat(game.shouldFinish()).isTrue();
    }

    @Test
    void multiplayerGameContinuesWhileAtLeastOnePlayerHasLives() {
        Game game = newGame(1, null);
        GamePlayer alive = new GamePlayer("Alive", 0, 1);
        GamePlayer eliminated = new GamePlayer("Eliminated", 1, 1);
        game.addPlayer(alive);
        game.addPlayer(eliminated);
        eliminated.recordIncorrectAnswer();

        assertThat(game.shouldFinish()).isFalse();

        alive.recordIncorrectAnswer();
        assertThat(game.shouldFinish()).isTrue();
    }
}
