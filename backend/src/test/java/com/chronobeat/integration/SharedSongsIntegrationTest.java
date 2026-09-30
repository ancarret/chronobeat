package com.chronobeat.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chronobeat.TestcontainersConfiguration;
import com.chronobeat.domain.Difficulty;
import com.chronobeat.domain.GameMode;
import com.chronobeat.domain.GameStatus;
import com.chronobeat.domain.MusicGenre;
import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.domain.PlayStyle;
import com.chronobeat.domain.RoundStatus;
import com.chronobeat.domain.Song;
import com.chronobeat.dto.game.AnswerOutcomeResponse;
import com.chronobeat.dto.game.AnswerRequest;
import com.chronobeat.dto.game.CreateGameRequest;
import com.chronobeat.dto.game.GamePhase;
import com.chronobeat.dto.game.GameResponse;
import com.chronobeat.dto.game.GameResultsResponse;
import com.chronobeat.dto.game.GameSettingsRequest;
import com.chronobeat.dto.game.GameStateResponse;
import com.chronobeat.dto.game.PlayerResponse;
import com.chronobeat.dto.game.RoundPendingResponse;
import com.chronobeat.dto.game.TimelineEntryResponse;
import com.chronobeat.exception.InvalidGameStateException;
import com.chronobeat.exception.RoundAlreadyLockedException;
import com.chronobeat.repository.GameRepository;
import com.chronobeat.repository.GameRoundRepository;
import com.chronobeat.repository.ProfileRepository;
import com.chronobeat.repository.SongRepository;
import com.chronobeat.service.GameService;
import com.chronobeat.service.GameStateService;
import com.chronobeat.service.TimelineValidationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The two play styles end to end on a shared device: how a shared-song round waits for the whole
 * table without leaking anything, the first-to-N race, timeouts, and the per-player state machine.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SharedSongsIntegrationTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private GameStateService gameStateService;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameRoundRepository gameRoundRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private TimelineValidationService timelineValidationService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        gameRepository.deleteAll();
        profileRepository.deleteAll();
        songRepository.deleteAll();
        // Distinct years, so a wrong placement always exists for any timeline.
        songRepository.saveAll(List.of(
                song("Fleetwood Mac", "Dreams", 1977),
                song("Nirvana", "Smells Like Teen Spirit", 1991),
                song("Oasis", "Wonderwall", 1995),
                song("Coldplay", "Viva La Vida", 2008),
                song("Daft Punk", "One More Time", 2000),
                song("Adele", "Rolling in the Deep", 2010),
                song("The Beatles", "Hey Jude", 1968),
                song("Dua Lipa", "Levitating", 2020),
                song("Queen", "Bohemian Rhapsody", 1975),
                song("Prince", "Purple Rain", 1984)));
    }

    private Song song(String artist, String title, int year) {
        return new Song(
                MusicProviderType.APPLE_MUSIC, UUID.randomUUID().toString(), title, artist, "Album",
                LocalDate.of(year, 6, 1), year, MusicGenre.POP, "Pop", "US", null, "https://preview.example/" + title, 200000);
    }

    private GameResponse newGame(PlayStyle style, Integer target, Integer answerSeconds, int lives, String... names) {
        GameSettingsRequest settings =
                new GameSettingsRequest(null, null, null, null, Difficulty.NORMAL, lives, null, style, target, answerSeconds);
        GameMode mode = names.length == 1 ? GameMode.SOLO : GameMode.LOCAL_MULTIPLAYER;
        return gameService.createGame(new CreateGameRequest(mode, List.of(names), settings));
    }

    /** Index the placement rules accept (or, when {@code correct} is false, reject) for this round. */
    private int placementIndex(UUID gameId, RoundPendingResponse round, boolean correct) {
        int mysteryYear = gameRoundRepository.findByIdWithSong(round.roundId()).orElseThrow().getSong().getEffectiveYear();
        List<Integer> years = gameService.getTimeline(gameId, round.playerId()).stream().map(TimelineEntryResponse::year).toList();
        Set<Integer> valid = timelineValidationService.computeValidIndices(years, mysteryYear);
        for (int i = 0; i <= years.size(); i++) {
            if (valid.contains(i) == correct) {
                return i;
            }
        }
        throw new IllegalStateException("No " + (correct ? "correct" : "wrong") + " index for this timeline");
    }

    /** Answers the player's pending round, right or wrong (an anchor needs no placement). */
    private AnswerOutcomeResponse answer(UUID gameId, UUID playerId, boolean correct) {
        RoundPendingResponse round = gameService.getCurrentRound(gameId, playerId);
        AnswerRequest request = new AnswerRequest(round.anchorRound() ? null : placementIndex(gameId, round, correct));
        return gameService.submitAnswer(gameId, round.roundId(), request);
    }

    private PlayerResponse player(UUID gameId, UUID playerId) {
        return gameService.getGame(gameId).players().stream().filter(p -> p.id().equals(playerId)).findFirst().orElseThrow();
    }

    @Test
    void sharedRoundDealsTheSameSongToEveryoneAndWaitsForTheWholeTable() {
        GameResponse created = newGame(PlayStyle.SHARED_SONGS, null, null, 3, "Ana", "Bea");
        UUID ana = created.players().get(0).id();
        UUID bea = created.players().get(1).id();
        gameService.startGame(created.id());

        RoundPendingResponse anaRound = gameService.getCurrentRound(created.id(), ana);
        RoundPendingResponse beaRound = gameService.getCurrentRound(created.id(), bea);
        assertThat(anaRound.roundNumber()).isEqualTo(beaRound.roundNumber());
        assertThat(anaRound.previewUrl()).isEqualTo(beaRound.previewUrl()); // the very same song
        assertThat(anaRound.roundId()).isNotEqualTo(beaRound.roundId());
        assertThat(anaRound.anchorRound() && beaRound.anchorRound()).isTrue();

        // Ana locks in first: nothing resolves, and nothing about the outcome exists yet.
        AnswerOutcomeResponse first = answer(created.id(), ana, true);
        assertThat(first.resolved()).isFalse();
        assertThat(first.waitingFor()).isEqualTo(1);
        assertThat(first.result()).isNull();
        assertThat(gameRoundRepository.findById(anaRound.roundId()).orElseThrow().getStatus()).isEqualTo(RoundStatus.LOCKED);
        assertThat(gameRoundRepository.findById(anaRound.roundId()).orElseThrow().getCorrect()).isNull();
        assertThat(gameService.getTimeline(created.id(), ana)).isEmpty(); // even the free anchor waits
        assertThat(player(created.id(), ana).answered()).isTrue();
        assertThat(player(created.id(), bea).answered()).isFalse();

        // Ana's own view says "waiting"; the shared device is handed to Bea.
        GameStateResponse anaView = gameStateService.stateFor(created.id(), ana, null);
        assertThat(anaView.phase()).isEqualTo(GamePhase.WAITING);
        assertThat(anaView.waitingOnPlayerIds()).containsExactly(bea);
        GameStateResponse next = gameStateService.stateFor(created.id(), null, null);
        assertThat(next.phase()).isEqualTo(GamePhase.ANSWERING);
        assertThat(next.viewerPlayerId()).isEqualTo(bea);
        assertThat(next.round().roundId()).isEqualTo(beaRound.roundId());

        // A locked answer can't be changed.
        assertThatThrownBy(() -> answer(created.id(), ana, true)).isInstanceOf(InvalidGameStateException.class);
        assertThatThrownBy(() -> gameService.submitAnswer(created.id(), anaRound.roundId(), new AnswerRequest(null)))
                .isInstanceOf(RoundAlreadyLockedException.class);
        assertThatThrownBy(() -> gameService.advance(created.id(), null, null)).isInstanceOf(InvalidGameStateException.class);

        // Bea is the last to answer, which resolves the whole round for both.
        AnswerOutcomeResponse last = answer(created.id(), bea, true);
        assertThat(last.resolved()).isTrue();
        assertThat(last.result().timeline()).hasSize(1);
        assertThat(gameService.getTimeline(created.id(), ana)).hasSize(1);

        GameStateResponse reveal = gameStateService.stateFor(created.id(), null, null);
        assertThat(reveal.phase()).isEqualTo(GamePhase.REVEAL);
        assertThat(reveal.summary().results()).hasSize(2);
        assertThat(reveal.summary().reveal().songId()).isNotNull();
        assertThat(reveal.summary().results()).allSatisfy(r -> assertThat(r.reveal().songId()).isEqualTo(reveal.summary().reveal().songId()));
    }

    @Test
    void mixedResultsInASharedRoundAreScoredPerPlayer() {
        GameResponse created = newGame(PlayStyle.SHARED_SONGS, null, null, 3, "Ana", "Bea");
        UUID ana = created.players().get(0).id();
        UUID bea = created.players().get(1).id();
        gameService.startGame(created.id());
        answer(created.id(), ana, true);
        answer(created.id(), bea, true);

        gameService.advance(created.id(), 1, null);
        answer(created.id(), ana, true);
        AnswerOutcomeResponse beaOutcome = answer(created.id(), bea, false);

        assertThat(beaOutcome.resolved()).isTrue();
        assertThat(beaOutcome.result().correct()).isFalse();
        assertThat(beaOutcome.result().validPositions()).isNotEmpty();
        PlayerResponse anaNow = player(created.id(), ana);
        PlayerResponse beaNow = player(created.id(), bea);
        assertThat(anaNow.score()).isPositive();
        assertThat(anaNow.timelineSize()).isEqualTo(2);
        assertThat(beaNow.score()).isZero();
        assertThat(beaNow.timelineSize()).isEqualTo(1);
        assertThat(beaNow.livesRemaining()).isEqualTo(2);

        GameStateResponse reveal = gameStateService.stateFor(created.id(), null, null);
        assertThat(reveal.summary().roundNumber()).isEqualTo(2);
        assertThat(reveal.summary().results().stream().filter(r -> r.correct()).count()).isEqualTo(1);
    }

    @Test
    void advancingIsIdempotentForPlayersWhoWatchedTheSameReveal() {
        GameResponse created = newGame(PlayStyle.SHARED_SONGS, null, null, 3, "Ana", "Bea");
        UUID ana = created.players().get(0).id();
        UUID bea = created.players().get(1).id();
        gameService.startGame(created.id());
        answer(created.id(), ana, true);
        answer(created.id(), bea, true);

        gameService.advance(created.id(), 1, null); // Ana taps "next"
        gameService.advance(created.id(), 1, null); // Bea taps it a moment later: no second round dealt

        assertThat(gameService.getGame(created.id()).currentRoundNumber()).isEqualTo(2);
        assertThat(gameStateService.stateFor(created.id(), ana, null).phase()).isEqualTo(GamePhase.ANSWERING);
    }

    @Test
    void sharedSongsAreNeverRepeatedWithinAGame() {
        GameResponse created = newGame(PlayStyle.SHARED_SONGS, null, null, 9, "Ana", "Bea");
        UUID ana = created.players().get(0).id();
        UUID bea = created.players().get(1).id();
        gameService.startGame(created.id());

        java.util.Set<String> previews = new java.util.HashSet<>();
        for (int round = 1; round <= 6; round++) {
            previews.add(gameService.getCurrentRound(created.id(), ana).previewUrl());
            answer(created.id(), ana, true);
            answer(created.id(), bea, false);
            if (round < 6) {
                gameService.advance(created.id(), round, null);
            }
        }
        assertThat(previews).hasSize(6);
    }

    @Test
    void firstToTheTargetWinsTheRaceAndOnlyWhenTheWholeRoundIsResolved() {
        GameResponse created = newGame(PlayStyle.SHARED_SONGS, 3, null, 5, "Ana", "Bea");
        UUID ana = created.players().get(0).id();
        UUID bea = created.players().get(1).id();
        gameService.startGame(created.id());
        answer(created.id(), ana, true);
        answer(created.id(), bea, true);

        gameService.advance(created.id(), 1, null);
        answer(created.id(), ana, true);
        answer(created.id(), bea, false);
        assertThat(gameService.getGame(created.id()).status()).isEqualTo(GameStatus.ACTIVE); // Ana has 2 of 3

        gameService.advance(created.id(), 2, null);
        answer(created.id(), ana, true);
        // Ana would reach 3 now, but the round isn't over until Bea answers too.
        assertThat(gameService.getGame(created.id()).status()).isEqualTo(GameStatus.ACTIVE);
        answer(created.id(), bea, false);

        assertThat(gameService.getGame(created.id()).status()).isEqualTo(GameStatus.FINISHED);
        GameResultsResponse results = gameService.getResults(created.id());
        assertThat(results.winningPlayerId()).isEqualTo(ana);

        GameStateResponse finished = gameStateService.stateFor(created.id(), null, null);
        assertThat(finished.phase()).isEqualTo(GamePhase.FINISHED);
        assertThat(finished.summary()).isNotNull(); // the final reveal is still on show
    }

    @Test
    void reachingTheTargetInTheSameRoundIsATieWhenEverythingElseIsEqual() {
        GameResponse created = newGame(PlayStyle.SHARED_SONGS, 3, null, 5, "Ana", "Bea");
        UUID ana = created.players().get(0).id();
        UUID bea = created.players().get(1).id();
        gameService.startGame(created.id());
        for (int round = 1; round <= 3; round++) {
            answer(created.id(), ana, true);
            answer(created.id(), bea, true);
            if (round < 3) {
                gameService.advance(created.id(), round, null);
            }
        }

        assertThat(gameService.getGame(created.id()).status()).isEqualTo(GameStatus.FINISHED);
        assertThat(gameService.getResults(created.id()).winningPlayerId()).isNull();
    }

    @Test
    void turnBasedRaceEndsTheMomentSomeoneReachesTheTarget() {
        GameResponse created = newGame(PlayStyle.TURN_BASED, 2, null, 5, "Ana", "Bea");
        UUID ana = created.players().get(0).id();
        UUID bea = created.players().get(1).id();
        gameService.startGame(created.id());

        // Turns alternate, each player hearing a song only they place.
        assertThat(gameStateService.stateFor(created.id(), null, null).viewerPlayerId()).isEqualTo(ana);
        assertThat(answer(created.id(), ana, true).resolved()).isTrue(); // anchor: Ana has 1
        assertThat(gameStateService.stateFor(created.id(), null, null).phase()).isEqualTo(GamePhase.REVEAL);

        gameService.advance(created.id(), 1, null);
        assertThat(gameStateService.stateFor(created.id(), null, null).viewerPlayerId()).isEqualTo(bea);
        answer(created.id(), bea, true); // anchor: Bea has 1
        gameService.advance(created.id(), 2, null);
        assertThat(gameStateService.stateFor(created.id(), null, null).viewerPlayerId()).isEqualTo(ana);

        answer(created.id(), ana, true); // Ana reaches 2 cards first

        assertThat(gameService.getGame(created.id()).status()).isEqualTo(GameStatus.FINISHED);
        assertThat(gameService.getResults(created.id()).winningPlayerId()).isEqualTo(ana);
    }

    @Test
    void playersWhoRunOutTheClockAreCountedAsMisses() {
        GameResponse created = newGame(PlayStyle.SHARED_SONGS, null, 10, 3, "Ana", "Bea");
        UUID ana = created.players().get(0).id();
        UUID bea = created.players().get(1).id();
        gameService.startGame(created.id());
        answer(created.id(), ana, true);
        answer(created.id(), bea, true);
        gameService.advance(created.id(), 1, null);

        answer(created.id(), ana, true);
        assertThat(gameStateService.stateFor(created.id(), bea, null).answerSecondsRemaining()).isBetween(1, 10);

        // Nothing is overdue yet...
        assertThat(gameService.findGamesWithOverdueRounds()).doesNotContain(created.id());

        // ...until Bea's round was dealt an hour ago and she never answered.
        jdbc.update("update game_rounds set created_at = now() - interval '1 hour' where game_id = ? and status = 'PENDING'", created.id());
        assertThat(gameService.findGamesWithOverdueRounds()).contains(created.id());

        gameService.expireOverdueRounds(created.id());

        assertThat(player(created.id(), bea).livesRemaining()).isEqualTo(2);
        assertThat(player(created.id(), ana).timelineSize()).isEqualTo(2);
        GameStateResponse reveal = gameStateService.stateFor(created.id(), null, null);
        assertThat(reveal.phase()).isEqualTo(GamePhase.REVEAL);
        assertThat(reveal.summary().results().stream().filter(r -> !r.correct()).count()).isEqualTo(1);
        assertThat(gameService.findGamesWithOverdueRounds()).doesNotContain(created.id());
    }

    @Test
    void anEliminatedPlayerStopsReceivingSharedRoundsAndTheGameEndsWhenEveryoneIsOut() {
        GameResponse created = newGame(PlayStyle.SHARED_SONGS, null, null, 1, "Ana", "Bea");
        UUID ana = created.players().get(0).id();
        UUID bea = created.players().get(1).id();
        gameService.startGame(created.id());
        answer(created.id(), ana, true);
        answer(created.id(), bea, true);
        gameService.advance(created.id(), 1, null);

        answer(created.id(), ana, true);
        answer(created.id(), bea, false); // Bea's only life is gone
        assertThat(player(created.id(), bea).eliminated()).isTrue();

        gameService.advance(created.id(), 2, null);
        assertThat(gameService.getCurrentRound(created.id(), ana)).isNotNull();
        assertThatThrownBy(() -> gameService.getCurrentRound(created.id(), bea)).isInstanceOf(InvalidGameStateException.class);
        assertThat(gameStateService.stateFor(created.id(), bea, null).phase()).isEqualTo(GamePhase.WAITING);

        answer(created.id(), ana, false);
        assertThat(gameService.getGame(created.id()).status()).isEqualTo(GameStatus.FINISHED);
    }
}
