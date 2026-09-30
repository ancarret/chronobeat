package com.chronobeat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
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

    // ---- races and winners ------------------------------------------------------------------

    private static Song song(int year) {
        return new Song(
                MusicProviderType.APPLE_MUSIC, UUID.randomUUID().toString(), "Song " + year, "Artist", "Album",
                LocalDate.of(year, 1, 1), year, MusicGenre.POP, "Pop", "US", null, "https://preview", null);
    }

    private static void giveCards(GamePlayer player, int count) {
        for (int i = 0; i < count; i++) {
            player.addToTimeline(new TimelineEntry(song(1960 + i), i, i + 1));
        }
    }

    private Game raceGame(Integer target) {
        GameSettings settings = new GameSettings(
                null, null, null, null, Difficulty.NORMAL, 3, null, PlayStyle.SHARED_SONGS, target, null);
        return new Game(GameMode.LOCAL_MULTIPLAYER, settings);
    }

    @Test
    void aRaceFinishesAsSoonAsAnyPlayerReachesTheTargetTimeline() {
        Game game = raceGame(3);
        GamePlayer ana = new GamePlayer("Ana", 0, 3);
        GamePlayer bea = new GamePlayer("Bea", 1, 3);
        game.addPlayer(ana);
        game.addPlayer(bea);

        giveCards(ana, 2);
        giveCards(bea, 1);
        assertThat(game.shouldFinish()).isFalse();
        assertThat(game.targetReached()).isFalse();

        ana.addToTimeline(new TimelineEntry(song(1990), 2, 3));
        assertThat(game.targetReached()).isTrue();
        assertThat(game.shouldFinish()).isTrue();
    }

    @Test
    void withoutATargetTimelineLengthNeverEndsTheGame() {
        Game game = raceGame(null);
        GamePlayer ana = new GamePlayer("Ana", 0, 3);
        game.addPlayer(ana);
        giveCards(ana, 50);

        assertThat(game.shouldFinish()).isFalse();
    }

    @Test
    void inARaceTheLongestTimelineWinsEvenWithALowerScore() {
        Game game = raceGame(5);
        GamePlayer longer = new GamePlayer("Longer", 0, 3);
        GamePlayer richer = new GamePlayer("Richer", 1, 3);
        game.addPlayer(longer);
        game.addPlayer(richer);
        giveCards(longer, 5);
        giveCards(richer, 4);
        richer.recordCorrectAnswer(900);

        assertThat(game.determineWinner()).contains(longer);
    }

    @Test
    void withoutARaceTheHighestScoreWinsEvenWithAShorterTimeline() {
        Game game = raceGame(null);
        GamePlayer longer = new GamePlayer("Longer", 0, 3);
        GamePlayer richer = new GamePlayer("Richer", 1, 3);
        game.addPlayer(longer);
        game.addPlayer(richer);
        giveCards(longer, 5);
        giveCards(richer, 2);
        richer.recordCorrectAnswer(900);

        assertThat(game.determineWinner()).contains(richer);
    }

    @Test
    void fewerMissesBreakATieOnTimelineAndScore() {
        Game game = raceGame(3);
        GamePlayer clean = new GamePlayer("Clean", 0, 5);
        GamePlayer sloppy = new GamePlayer("Sloppy", 1, 5);
        game.addPlayer(clean);
        game.addPlayer(sloppy);
        giveCards(clean, 3);
        giveCards(sloppy, 3);
        sloppy.recordIncorrectAnswer();

        assertThat(game.determineWinner()).contains(clean);
    }

    @Test
    void identicalPlayersTie() {
        Game game = raceGame(3);
        GamePlayer ana = new GamePlayer("Ana", 0, 3);
        GamePlayer bea = new GamePlayer("Bea", 1, 3);
        game.addPlayer(ana);
        game.addPlayer(bea);
        giveCards(ana, 3);
        giveCards(bea, 3);

        assertThat(game.determineWinner()).isEmpty();
    }

    @Test
    void aSoloPlayerAlwaysWinsTheirOwnGame() {
        Game game = newGame(3, null);
        GamePlayer solo = new GamePlayer("Solo", 0, 3);
        game.addPlayer(solo);

        assertThat(game.determineWinner()).contains(solo);
    }
}
