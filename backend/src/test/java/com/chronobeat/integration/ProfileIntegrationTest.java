package com.chronobeat.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chronobeat.TestcontainersConfiguration;
import com.chronobeat.domain.Difficulty;
import com.chronobeat.domain.GameMode;
import com.chronobeat.domain.GameStatus;
import com.chronobeat.domain.MusicGenre;
import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.domain.Profile;
import com.chronobeat.domain.Song;
import com.chronobeat.dto.game.AnswerRequest;
import com.chronobeat.dto.game.CreateGameRequest;
import com.chronobeat.dto.game.GameResponse;
import com.chronobeat.dto.game.GameResultsResponse;
import com.chronobeat.dto.game.GameSettingsRequest;
import com.chronobeat.dto.game.RecordsBrokenResponse;
import com.chronobeat.dto.game.RoundPendingResponse;
import com.chronobeat.dto.game.StartGameResponse;
import com.chronobeat.dto.game.TimelineEntryResponse;
import com.chronobeat.dto.profile.ProfileCreatedResponse;
import com.chronobeat.dto.profile.ProfileStatsResponse;
import com.chronobeat.exception.InvalidGameStateException;
import com.chronobeat.exception.InvalidProfileTokenException;
import com.chronobeat.repository.GameRepository;
import com.chronobeat.repository.GameRoundRepository;
import com.chronobeat.repository.ProfileRepository;
import com.chronobeat.repository.SongRepository;
import com.chronobeat.service.GameService;
import com.chronobeat.service.ProfileService;
import com.chronobeat.service.ProfileStatsService;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * Anonymous profiles end to end against a real Postgres: token issuance, linking games to a
 * profile, the personal-records read model and the "did this game set a record" verdicts.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProfileIntegrationTest {

    @Autowired
    private ProfileService profileService;

    @Autowired
    private ProfileStatsService profileStatsService;

    @Autowired
    private GameService gameService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameRoundRepository gameRoundRepository;

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private TimelineValidationService timelineValidationService;

    @BeforeEach
    void seed() {
        // Games first: they reference both profiles (SET NULL) and songs (via rounds).
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
                song("Dua Lipa", "Levitating", 2020)));
    }

    private Song song(String artist, String title, int year) {
        return new Song(
                MusicProviderType.APPLE_MUSIC, UUID.randomUUID().toString(), title, artist, "Album",
                LocalDate.of(year, 6, 1), year, MusicGenre.POP, "Pop", "US", null, "https://preview.example/" + title, 200000);
    }

    private Profile newProfile(String nickname) {
        ProfileCreatedResponse created = profileService.create(nickname);
        return profileService.authenticate(created.token());
    }

    private CreateGameRequest soloRequest() {
        GameSettingsRequest settings = new GameSettingsRequest(null, null, null, null, Difficulty.NORMAL, 1, null);
        return new CreateGameRequest(GameMode.SOLO, List.of("Andres"), settings);
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

    /**
     * Plays a one-life solo game: the free anchor, {@code correctPlacements} right answers, then
     * one wrong answer that ends the game. Returns the game id.
     */
    private UUID playOneLifeGame(Profile profile, int correctPlacements) {
        GameResponse created = gameService.createGame(soloRequest(), profile);
        UUID gameId = created.id();
        StartGameResponse started = gameService.startGame(gameId);
        gameService.submitAnswer(gameId, started.currentRound().roundId(), new AnswerRequest(null));

        for (int i = 0; i < correctPlacements; i++) {
            RoundPendingResponse round = gameService.nextRound(gameId);
            gameService.submitAnswer(gameId, round.roundId(), new AnswerRequest(placementIndex(gameId, round, true)));
        }
        RoundPendingResponse last = gameService.nextRound(gameId);
        var result = gameService.submitAnswer(gameId, last.roundId(), new AnswerRequest(placementIndex(gameId, last, false)));
        assertThat(result.gameStatus()).isEqualTo(GameStatus.FINISHED);
        return gameId;
    }

    @Test
    void tokenIsIssuedOnceAndOnlyItsHashIsStored() {
        ProfileCreatedResponse created = profileService.create("  Ana  ");

        assertThat(created.nickname()).isEqualTo("Ana");
        assertThat(created.token()).hasSizeGreaterThanOrEqualTo(40);

        Profile stored = profileRepository.findById(created.id()).orElseThrow();
        assertThat(stored.getTokenHash()).isNotEqualTo(created.token()).hasSize(64);
        assertThat(profileService.findByToken(created.token())).isPresent();
        assertThat(profileService.findByToken("not-the-token")).isEmpty();
        assertThat(profileService.findByToken(null)).isEmpty();
        assertThatThrownBy(() -> profileService.authenticate("not-the-token")).isInstanceOf(InvalidProfileTokenException.class);
    }

    @Test
    void renamingRequiresTheTokenAndKeepsTheId() {
        ProfileCreatedResponse created = profileService.create("Ana");

        var renamed = profileService.rename(created.token(), " Anita ");

        assertThat(renamed.id()).isEqualTo(created.id());
        assertThat(renamed.nickname()).isEqualTo("Anita");
        assertThatThrownBy(() -> profileService.rename("bogus", "Hacker")).isInstanceOf(InvalidProfileTokenException.class);
    }

    @Test
    void linkedGamesFeedPersonalRecordsAndHistory() {
        Profile profile = newProfile("Ana");

        playOneLifeGame(profile, 2);

        ProfileStatsResponse stats = profileStatsService.statsFor(profile);
        assertThat(stats.gamesPlayed()).isEqualTo(1);
        assertThat(stats.longestTimeline()).isEqualTo(3); // anchor + 2 correct placements
        assertThat(stats.totalCorrect()).isEqualTo(2);
        assertThat(stats.totalIncorrect()).isEqualTo(1);
        assertThat(stats.accuracy()).isEqualTo(2.0 / 3.0);
        assertThat(stats.bestStreak()).isEqualTo(2);
        assertThat(stats.bestScore()).isPositive();
        assertThat(stats.byDecade()).isNotEmpty();
        assertThat(stats.byDecade().stream().mapToInt(b -> b.total()).sum()).isEqualTo(3); // the anchor is excluded
        assertThat(stats.byGenre()).singleElement().satisfies(b -> {
            assertThat(b.key()).isEqualTo("POP");
            assertThat(b.correct()).isEqualTo(2);
            assertThat(b.total()).isEqualTo(3);
        });
        assertThat(stats.recentGames()).singleElement().satisfies(g -> {
            assertThat(g.timelineSize()).isEqualTo(3);
            assertThat(g.score()).isEqualTo(stats.bestScore());
        });
    }

    @Test
    void resultsReportWhichRecordsEachGameBroke() {
        Profile profile = newProfile("Ana");

        UUID first = playOneLifeGame(profile, 1);
        UUID worse = playOneLifeGame(profile, 0);
        UUID better = playOneLifeGame(profile, 3);

        RecordsBrokenResponse firstRecords = onlyRecords(first);
        assertThat(firstRecords.firstGame()).isTrue();
        assertThat(firstRecords.bestScore() || firstRecords.longestTimeline() || firstRecords.bestStreak()).isFalse();

        RecordsBrokenResponse worseRecords = onlyRecords(worse);
        assertThat(worseRecords.firstGame()).isFalse();
        assertThat(worseRecords.bestScore()).isFalse();
        assertThat(worseRecords.longestTimeline()).isFalse();
        assertThat(worseRecords.bestStreak()).isFalse();

        RecordsBrokenResponse betterRecords = onlyRecords(better);
        assertThat(betterRecords.firstGame()).isFalse();
        assertThat(betterRecords.bestScore()).isTrue();
        assertThat(betterRecords.longestTimeline()).isTrue();
        assertThat(betterRecords.bestStreak()).isTrue();

        // Verdicts are pinned to the games that came before, so a later, better game can't rewrite history.
        assertThat(onlyRecords(first).firstGame()).isTrue();
        assertThat(onlyRecords(better).bestScore()).isTrue();
    }

    private RecordsBrokenResponse onlyRecords(UUID gameId) {
        GameResultsResponse results = gameService.getResults(gameId);
        assertThat(results.records()).hasSize(1);
        return results.records().get(0);
    }

    @Test
    void guestGamesAreNotTrackedAndDoNotAffectAProfile() {
        Profile profile = newProfile("Ana");

        UUID guestGame = playOneLifeGame(null, 2);

        assertThat(gameService.getResults(guestGame).records()).isEmpty();
        ProfileStatsResponse stats = profileStatsService.statsFor(profile);
        assertThat(stats.gamesPlayed()).isZero();
        assertThat(stats.longestTimeline()).isZero();
        assertThat(stats.byDecade()).isEmpty();
        assertThat(stats.recentGames()).isEmpty();
    }

    @Test
    void multiplayerLinksOnlyTheRequestedPlayerAndRejectsBadIndexes() {
        Profile profile = newProfile("Ana");
        GameSettingsRequest settings = new GameSettingsRequest(null, null, null, null, Difficulty.NORMAL, 1, null);

        GameResponse created = gameService.createGame(
                new CreateGameRequest(GameMode.LOCAL_MULTIPLAYER, List.of("Bea", "Ana", "Carlos"), settings, 1), profile);
        var linked = gameRepository.findWithPlayersById(created.id()).orElseThrow().getPlayers();
        assertThat(linked.stream().filter(p -> p.getProfile() != null).map(p -> p.getDisplayName())).containsExactly("Ana");

        // Without an explicit index nobody is linked in multiplayer: it's ambiguous who "you" are.
        GameResponse unlinked = gameService.createGame(
                new CreateGameRequest(GameMode.LOCAL_MULTIPLAYER, List.of("Bea", "Ana"), settings), profile);
        assertThat(gameRepository.findWithPlayersById(unlinked.id()).orElseThrow().getPlayers())
                .allSatisfy(p -> assertThat(p.getProfile()).isNull());

        assertThatThrownBy(() -> gameService.createGame(
                        new CreateGameRequest(GameMode.LOCAL_MULTIPLAYER, List.of("Bea", "Ana"), settings, 5), profile))
                .isInstanceOf(InvalidGameStateException.class);
    }
}
