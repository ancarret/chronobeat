package com.chronobeat.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chronobeat.TestcontainersConfiguration;
import com.chronobeat.domain.Difficulty;
import com.chronobeat.domain.GameMode;
import com.chronobeat.domain.GameStatus;
import com.chronobeat.domain.MusicGenre;
import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.domain.Song;
import com.chronobeat.dto.game.AnswerRequest;
import com.chronobeat.dto.game.CreateGameRequest;
import com.chronobeat.dto.game.GameResponse;
import com.chronobeat.dto.game.GameResultsResponse;
import com.chronobeat.dto.game.GameSettingsRequest;
import com.chronobeat.dto.game.RoundPendingResponse;
import com.chronobeat.dto.game.RoundResultResponse;
import com.chronobeat.dto.game.StartGameResponse;
import com.chronobeat.exception.InvalidGameStateException;
import com.chronobeat.exception.InvalidPlacementException;
import com.chronobeat.exception.RoundAlreadyResolvedException;
import com.chronobeat.repository.GameRoundRepository;
import com.chronobeat.repository.SongRepository;
import com.chronobeat.service.GameService;
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
 * End-to-end test of the game lifecycle against a real Postgres (Testcontainers)
 * and the real Spring context, covering the full MVP acceptance flow: create
 * game -> start -> anchor round -> correct/incorrect placement -> elimination ->
 * results, plus the duplicate-submission and out-of-bounds guards.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class GameServiceIntegrationTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private com.chronobeat.repository.GameRepository gameRepository;

    @Autowired
    private GameRoundRepository gameRoundRepository;

    @Autowired
    private TimelineValidationService timelineValidationService;

    @BeforeEach
    void seedCatalog() {
        // Games must go first: game_rounds references songs, and only deleting the
        // owning game cascades those rounds away before we can clear the song catalog.
        gameRepository.deleteAll();
        songRepository.deleteAll();
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

    private CreateGameRequest soloGameRequest(int maxLives) {
        GameSettingsRequest settings = new GameSettingsRequest(null, null, null, null, Difficulty.NORMAL, maxLives, null);
        return new CreateGameRequest(GameMode.SOLO, List.of("Andres"), settings);
    }

    /**
     * Song selection is genuinely random (by design, so players can't predict rounds),
     * and the API deliberately never reveals a pending round's answer. For test
     * determinism we peek at the real mystery year directly via the repository (a
     * white-box trick only the test harness is allowed) and compute an index the
     * {@link TimelineValidationService} is guaranteed to mark wrong.
     */
    private int findGuaranteedWrongIndex(UUID gameId, UUID roundId, UUID playerId) {
        int mysteryYear = gameRoundRepository.findByIdWithSong(roundId).orElseThrow().getSong().getEffectiveYear();
        List<Integer> timelineYears =
                gameService.getTimeline(gameId, playerId).stream().map(com.chronobeat.dto.game.TimelineEntryResponse::year).toList();
        Set<Integer> validIndices = timelineValidationService.computeValidIndices(timelineYears, mysteryYear);
        for (int i = 0; i <= timelineYears.size(); i++) {
            if (!validIndices.contains(i)) {
                return i;
            }
        }
        throw new IllegalStateException("No wrong index exists for this timeline/mystery year combination");
    }

    @Test
    void fullSoloGameFlowMatchesAcceptanceCriteria() {
        GameResponse created = gameService.createGame(soloGameRequest(2));
        assertThat(created.status()).isEqualTo(GameStatus.CREATED);
        assertThat(created.players()).hasSize(1);

        StartGameResponse started = gameService.startGame(created.id());
        assertThat(started.game().status()).isEqualTo(GameStatus.ACTIVE);
        assertThat(started.currentRound().anchorRound()).isTrue();
        assertThat(started.currentRound().roundNumber()).isEqualTo(1);

        // Anchor round: no placement decision, song is auto-added to the timeline.
        RoundResultResponse anchorResult = gameService.submitAnswer(
                created.id(), started.currentRound().roundId(), new AnswerRequest(null));
        assertThat(anchorResult.correct()).isTrue();
        assertThat(anchorResult.timeline()).hasSize(1);
        assertThat(anchorResult.player().score()).isZero(); // anchor round is not a scored guess

        RoundPendingResponse round2 = gameService.nextRound(created.id());
        assertThat(round2.anchorRound()).isFalse();
        assertThat(round2.allowedPositionCount()).isEqualTo(2); // 1 existing entry -> 2 valid slots

        // Deliberately answer with an out-of-range index.
        assertThatThrownBy(() -> gameService.submitAnswer(created.id(), round2.roundId(), new AnswerRequest(99)))
                .isInstanceOf(InvalidPlacementException.class);

        UUID playerId = created.players().get(0).id();
        int wrongIndex = findGuaranteedWrongIndex(created.id(), round2.roundId(), playerId);
        RoundResultResponse wrongResult = gameService.submitAnswer(created.id(), round2.roundId(), new AnswerRequest(wrongIndex));

        assertThat(wrongResult.correct()).isFalse();
        assertThat(wrongResult.player().livesRemaining()).isLessThan(2);
    }

    @Test
    void answeringAnAlreadyResolvedRoundIsRejected() {
        GameResponse created = gameService.createGame(soloGameRequest(3));
        StartGameResponse started = gameService.startGame(created.id());
        UUID roundId = started.currentRound().roundId();

        gameService.submitAnswer(created.id(), roundId, new AnswerRequest(null));

        assertThatThrownBy(() -> gameService.submitAnswer(created.id(), roundId, new AnswerRequest(null)))
                .isInstanceOf(RoundAlreadyResolvedException.class);
    }

    @Test
    void requestingNextRoundBeforeAnsweringIsRejected() {
        GameResponse created = gameService.createGame(soloGameRequest(3));
        gameService.startGame(created.id());

        assertThatThrownBy(() -> gameService.nextRound(created.id())).isInstanceOf(InvalidGameStateException.class);
    }

    @Test
    void gameFinishesOnceLivesAreExhaustedAndResultsBecomeAvailable() {
        GameResponse created = gameService.createGame(soloGameRequest(1));
        UUID playerId = created.players().get(0).id();
        StartGameResponse started = gameService.startGame(created.id());
        gameService.submitAnswer(created.id(), started.currentRound().roundId(), new AnswerRequest(null));

        RoundPendingResponse round2 = gameService.nextRound(created.id());
        int wrongIndex = findGuaranteedWrongIndex(created.id(), round2.roundId(), playerId);
        RoundResultResponse result = gameService.submitAnswer(created.id(), round2.roundId(), new AnswerRequest(wrongIndex));

        assertThat(result.correct()).isFalse();
        assertThat(result.gameStatus()).isEqualTo(GameStatus.FINISHED);

        GameResultsResponse results = gameService.getResults(created.id());
        assertThat(results.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(results.players()).hasSize(1);
        assertThat(results.winningPlayerId()).isEqualTo(results.players().get(0).id());
    }
}
