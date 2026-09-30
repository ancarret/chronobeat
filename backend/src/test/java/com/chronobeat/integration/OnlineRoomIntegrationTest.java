package com.chronobeat.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chronobeat.TestcontainersConfiguration;
import com.chronobeat.domain.Difficulty;
import com.chronobeat.domain.GameMode;
import com.chronobeat.domain.GamePlayer;
import com.chronobeat.domain.GameStatus;
import com.chronobeat.domain.MusicGenre;
import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.domain.PlayStyle;
import com.chronobeat.domain.Profile;
import com.chronobeat.domain.Song;
import com.chronobeat.dto.game.AnswerOutcomeResponse;
import com.chronobeat.dto.game.AnswerRequest;
import com.chronobeat.dto.game.CreateGameRequest;
import com.chronobeat.dto.game.GamePhase;
import com.chronobeat.dto.game.GameSettingsRequest;
import com.chronobeat.dto.game.GameStateResponse;
import com.chronobeat.dto.game.RoundPendingResponse;
import com.chronobeat.dto.game.TimelineEntryResponse;
import com.chronobeat.dto.room.CreateRoomRequest;
import com.chronobeat.dto.room.JoinRoomRequest;
import com.chronobeat.dto.room.RoomInfoResponse;
import com.chronobeat.dto.room.RoomJoinedResponse;
import com.chronobeat.event.GameChangedEvent;
import com.chronobeat.event.GameEventType;
import com.chronobeat.exception.InvalidGameStateException;
import com.chronobeat.exception.InvalidPlayerTokenException;
import com.chronobeat.exception.NotYourRoundException;
import com.chronobeat.exception.RoomNotFoundException;
import com.chronobeat.repository.GameRepository;
import com.chronobeat.repository.GameRoundRepository;
import com.chronobeat.repository.ProfileRepository;
import com.chronobeat.repository.SongRepository;
import com.chronobeat.service.GameService;
import com.chronobeat.service.GameStateService;
import com.chronobeat.service.ProfileService;
import com.chronobeat.service.RoomService;
import com.chronobeat.service.TimelineValidationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** Online rooms: lobby management, per-player tokens, and playing a shared-song round from two devices. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class OnlineRoomIntegrationTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private GameService gameService;

    @Autowired
    private GameStateService gameStateService;

    @Autowired
    private ProfileService profileService;

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

    @Autowired
    private ApplicationEvents events;

    @BeforeEach
    void seed() {
        gameRepository.deleteAll();
        profileRepository.deleteAll();
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

    private GameSettingsRequest sharedSettings() {
        return new GameSettingsRequest(null, null, null, null, Difficulty.NORMAL, 3, null, PlayStyle.SHARED_SONGS, null, null);
    }

    private RoomJoinedResponse openRoom(String host) {
        return roomService.createRoom(new CreateRoomRequest(host, sharedSettings()), null);
    }

    private RoomJoinedResponse join(RoomJoinedResponse room, String nickname) {
        return roomService.join(room.roomCode(), new JoinRoomRequest(nickname), null);
    }

    private List<GameEventType> eventTypes() {
        return events.stream(GameChangedEvent.class).map(GameChangedEvent::type).toList();
    }

    private int placementIndex(UUID gameId, RoundPendingResponse round, boolean correct) {
        int mysteryYear = gameRoundRepository.findByIdWithSong(round.roundId()).orElseThrow().getSong().getEffectiveYear();
        List<Integer> years = gameService.getTimeline(gameId, round.playerId()).stream().map(TimelineEntryResponse::year).toList();
        Set<Integer> valid = timelineValidationService.computeValidIndices(years, mysteryYear);
        for (int i = 0; i <= years.size(); i++) {
            if (valid.contains(i) == correct) {
                return i;
            }
        }
        throw new IllegalStateException("no index");
    }

    /** Answers on behalf of one online player, using only what that player's own device would send. */
    private AnswerOutcomeResponse answer(RoomJoinedResponse who, boolean correct) {
        GameStateResponse state = gameStateService.stateFor(who.gameId(), null, who.playerToken());
        assertThat(state.phase()).isEqualTo(GamePhase.ANSWERING);
        RoundPendingResponse round = state.round();
        AnswerRequest request = new AnswerRequest(round.anchorRound() ? null : placementIndex(who.gameId(), round, correct));
        return gameService.submitAnswer(who.gameId(), round.roundId(), request, who.playerToken());
    }

    // ------------------------------------------------------------------ lobby

    @Test
    void openingARoomMakesTheCallerTheHostWithASecretToken() {
        RoomJoinedResponse room = openRoom("Ana");

        assertThat(room.roomCode()).matches("[BCDFGHJKLMNPQRSTVWXZ]{4,5}");
        assertThat(room.game().mode()).isEqualTo(GameMode.ONLINE_MULTIPLAYER);
        assertThat(room.game().status()).isEqualTo(GameStatus.CREATED);
        assertThat(room.game().settings().playStyle()).isEqualTo(PlayStyle.SHARED_SONGS);
        assertThat(room.game().settings().answerSeconds()).as("online games always run on a clock").isPositive();
        assertThat(room.game().players()).singleElement().satisfies(p -> {
            assertThat(p.host()).isTrue();
            assertThat(p.displayName()).isEqualTo("Ana");
        });

        GamePlayer stored = gameRepository.findWithPlayersById(room.gameId()).orElseThrow().getPlayers().get(0);
        assertThat(stored.getTokenHash()).isNotEqualTo(room.playerToken()).hasSize(64);
    }

    @Test
    void aRoomCanBePreviewedAndJoinedByCodeInAnyCase() {
        RoomJoinedResponse room = openRoom("Ana");

        RoomInfoResponse info = roomService.lookup(room.roomCode().toLowerCase());
        assertThat(info.gameId()).isEqualTo(room.gameId());
        assertThat(info.hostName()).isEqualTo("Ana");
        assertThat(info.playerCount()).isEqualTo(1);

        RoomJoinedResponse bea = roomService.join(" " + room.roomCode().toLowerCase() + " ", new JoinRoomRequest("Bea"), null);
        assertThat(bea.gameId()).isEqualTo(room.gameId());
        assertThat(bea.playerToken()).isNotEqualTo(room.playerToken());
        assertThat(bea.game().players()).hasSize(2);
        assertThat(bea.game().players().stream().filter(p -> p.host()).count()).isEqualTo(1);
        assertThat(eventTypes()).contains(GameEventType.LOBBY_CHANGED);

        assertThatThrownBy(() -> roomService.lookup("ZZZZ")).isInstanceOf(RoomNotFoundException.class);
        assertThatThrownBy(() -> roomService.join("ZZZZ", new JoinRoomRequest("Eve"), null)).isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void duplicateNicknamesAreDisambiguatedAndTheRoomCapsAtEightPlayers() {
        RoomJoinedResponse room = openRoom("Ana");
        RoomJoinedResponse second = join(room, "ana");
        assertThat(second.game().players().stream().map(p -> p.displayName())).containsExactly("Ana", "ana (2)");

        IntStream.rangeClosed(3, 8).forEach(i -> join(room, "Player " + i));
        assertThat(gameService.getGame(room.gameId()).players()).hasSize(8);
        assertThatThrownBy(() -> join(room, "One too many")).isInstanceOf(InvalidGameStateException.class);
    }

    @Test
    void aProfileCannotTakeTwoSeatsInTheSameRoom() {
        Profile profile = profileService.authenticate(profileService.create("Ana").token());
        RoomJoinedResponse room = roomService.createRoom(new CreateRoomRequest("Ana", sharedSettings()), profile);

        assertThatThrownBy(() -> roomService.join(room.roomCode(), new JoinRoomRequest("Ana again"), profile))
                .isInstanceOf(InvalidGameStateException.class);
    }

    @Test
    void onlyTheHostWithEnoughPlayersCanStartAndOnlyOnce() {
        RoomJoinedResponse host = openRoom("Ana");

        assertThatThrownBy(() -> gameService.startGame(host.gameId(), host.playerToken()))
                .isInstanceOf(InvalidGameStateException.class); // alone in the room

        RoomJoinedResponse guest = join(host, "Bea");
        assertThatThrownBy(() -> gameService.startGame(host.gameId(), guest.playerToken())).isInstanceOf(NotYourRoundException.class);
        assertThatThrownBy(() -> gameService.startGame(host.gameId(), null)).isInstanceOf(InvalidPlayerTokenException.class);
        assertThatThrownBy(() -> gameService.startGame(host.gameId(), "made-up")).isInstanceOf(InvalidPlayerTokenException.class);

        gameService.startGame(host.gameId(), host.playerToken());
        assertThat(gameService.getGame(host.gameId()).status()).isEqualTo(GameStatus.ACTIVE);
        assertThat(eventTypes()).contains(GameEventType.GAME_STARTED);
        assertThatThrownBy(() -> gameService.startGame(host.gameId(), host.playerToken())).isInstanceOf(InvalidGameStateException.class);

        // Once started the code is spent: nobody else can wander in.
        assertThatThrownBy(() -> join(host, "Late")).isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void leavingPassesTheHostToTheNextPlayerAndClosesAnEmptyRoom() {
        RoomJoinedResponse ana = openRoom("Ana");
        RoomJoinedResponse bea = join(ana, "Bea");
        RoomJoinedResponse cy = join(ana, "Cy");

        roomService.leave(bea.gameId(), bea.playerToken());
        assertThat(gameService.getGame(ana.gameId()).players()).extracting(p -> p.displayName()).containsExactly("Ana", "Cy");

        roomService.leave(ana.gameId(), ana.playerToken()); // the host walks out
        assertThat(gameService.getGame(ana.gameId()).players()).singleElement().satisfies(p -> {
            assertThat(p.displayName()).isEqualTo("Cy");
            assertThat(p.host()).isTrue();
        });

        roomService.leave(cy.gameId(), cy.playerToken());
        assertThat(gameRepository.findById(ana.gameId()).orElseThrow().getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThatThrownBy(() -> roomService.lookup(ana.roomCode())).isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void onlyTheHostCanRemoveOthersAndNotThemselves() {
        RoomJoinedResponse ana = openRoom("Ana");
        RoomJoinedResponse bea = join(ana, "Bea");
        RoomJoinedResponse cy = join(ana, "Cy");

        assertThatThrownBy(() -> roomService.kick(ana.gameId(), cy.playerId(), bea.playerToken())).isInstanceOf(NotYourRoundException.class);
        assertThatThrownBy(() -> roomService.kick(ana.gameId(), ana.playerId(), ana.playerToken())).isInstanceOf(InvalidGameStateException.class);

        roomService.kick(ana.gameId(), cy.playerId(), ana.playerToken());
        assertThat(gameService.getGame(ana.gameId()).players()).extracting(p -> p.displayName()).containsExactly("Ana", "Bea");

        // The removed player's token stops working.
        assertThatThrownBy(() -> gameStateService.stateFor(ana.gameId(), null, cy.playerToken())).isInstanceOf(InvalidPlayerTokenException.class);

        gameService.startGame(ana.gameId(), ana.playerToken());
        assertThatThrownBy(() -> roomService.kick(ana.gameId(), bea.playerId(), ana.playerToken())).isInstanceOf(InvalidGameStateException.class);
        assertThatThrownBy(() -> roomService.leave(ana.gameId(), bea.playerToken())).isInstanceOf(InvalidGameStateException.class);
    }

    @Test
    void sharedDeviceGamesCannotBeCreatedAsOnline() {
        GameSettingsRequest settings = sharedSettings();
        assertThatThrownBy(() -> gameService.createGame(new CreateGameRequest(GameMode.ONLINE_MULTIPLAYER, List.of("A", "B"), settings)))
                .isInstanceOf(InvalidGameStateException.class);
    }

    // ------------------------------------------------------------------ playing

    @Test
    void twoDevicesPlayASharedRoundUsingOnlyTheirOwnTokens() {
        RoomJoinedResponse ana = openRoom("Ana");
        RoomJoinedResponse bea = join(ana, "Bea");

        GameStateResponse lobby = gameStateService.stateFor(ana.gameId(), null, ana.playerToken());
        assertThat(lobby.phase()).isEqualTo(GamePhase.LOBBY);
        assertThat(lobby.viewerPlayerId()).isEqualTo(ana.playerId());

        gameService.startGame(ana.gameId(), ana.playerToken());

        GameStateResponse anaView = gameStateService.stateFor(ana.gameId(), null, ana.playerToken());
        GameStateResponse beaView = gameStateService.stateFor(ana.gameId(), null, bea.playerToken());
        assertThat(anaView.viewerPlayerId()).isEqualTo(ana.playerId());
        assertThat(beaView.viewerPlayerId()).isEqualTo(bea.playerId());
        assertThat(anaView.round().previewUrl()).isEqualTo(beaView.round().previewUrl()); // same song
        assertThat(anaView.answerSecondsRemaining()).isBetween(1, 60);

        // Nobody can answer for somebody else, or without saying who they are.
        AnswerRequest anchor = new AnswerRequest(null);
        assertThatThrownBy(() -> gameService.submitAnswer(ana.gameId(), beaView.round().roundId(), anchor, ana.playerToken()))
                .isInstanceOf(NotYourRoundException.class);
        assertThatThrownBy(() -> gameService.submitAnswer(ana.gameId(), beaView.round().roundId(), anchor, null))
                .isInstanceOf(InvalidPlayerTokenException.class);
        assertThatThrownBy(() -> gameStateService.stateFor(ana.gameId(), bea.playerId(), ana.playerToken()))
                .isInstanceOf(NotYourRoundException.class);
        assertThatThrownBy(() -> gameStateService.stateFor(ana.gameId(), null, null)).isInstanceOf(InvalidPlayerTokenException.class);

        assertThat(answer(ana, true).resolved()).isFalse();
        assertThat(eventTypes()).contains(GameEventType.ANSWER_LOCKED);
        GameStateResponse anaWaiting = gameStateService.stateFor(ana.gameId(), null, ana.playerToken());
        assertThat(anaWaiting.phase()).isEqualTo(GamePhase.WAITING);
        assertThat(anaWaiting.waitingOnPlayerIds()).containsExactly(bea.playerId());
        assertThat(anaWaiting.game().players().stream().filter(p -> p.answered()).map(p -> p.id())).containsExactly(ana.playerId());

        assertThat(answer(bea, true).resolved()).isTrue();
        assertThat(eventTypes()).contains(GameEventType.ROUND_RESOLVED);
        assertThat(gameStateService.stateFor(ana.gameId(), null, ana.playerToken()).phase()).isEqualTo(GamePhase.REVEAL);
        assertThat(gameStateService.stateFor(ana.gameId(), null, bea.playerToken()).summary().results()).hasSize(2);
    }

    @Test
    void theRevealStaysUpBrieflyBeforeAnyoneCanSkipToTheNextRound() {
        RoomJoinedResponse ana = openRoom("Ana");
        RoomJoinedResponse bea = join(ana, "Bea");
        gameService.startGame(ana.gameId(), ana.playerToken());
        answer(ana, true);
        answer(bea, true);

        assertThatThrownBy(() -> gameService.advance(ana.gameId(), 1, ana.playerToken()))
                .isInstanceOf(InvalidGameStateException.class)
                .hasMessageContaining("reveal");
        assertThatThrownBy(() -> gameService.advance(ana.gameId(), 1, null)).isInstanceOf(InvalidPlayerTokenException.class);

        // Once the reveal has been on screen long enough, whoever taps first deals the round; the rest are no-ops.
        jdbc.update("update game_rounds set resolved_at = ? where game_id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(60)), ana.gameId());
        gameService.advance(ana.gameId(), 1, ana.playerToken());
        gameService.advance(ana.gameId(), 1, bea.playerToken());

        assertThat(gameService.getGame(ana.gameId()).currentRoundNumber()).isEqualTo(2);
        assertThat(eventTypes()).contains(GameEventType.ROUND_DEALT);
        assertThat(gameStateService.stateFor(ana.gameId(), null, bea.playerToken()).phase()).isEqualTo(GamePhase.ANSWERING);
    }

    @Test
    void aTurnBasedRoomLetsOnlyTheActivePlayerActWhileTheOthersWatch() {
        GameSettingsRequest settings = new GameSettingsRequest(null, null, null, null, Difficulty.NORMAL, 3, null, PlayStyle.TURN_BASED, null, null);
        RoomJoinedResponse ana = roomService.createRoom(new CreateRoomRequest("Ana", settings), null);
        RoomJoinedResponse bea = join(ana, "Bea");
        gameService.startGame(ana.gameId(), ana.playerToken());

        assertThat(gameStateService.stateFor(ana.gameId(), null, ana.playerToken()).phase()).isEqualTo(GamePhase.ANSWERING);
        GameStateResponse beaWatching = gameStateService.stateFor(ana.gameId(), null, bea.playerToken());
        assertThat(beaWatching.phase()).isEqualTo(GamePhase.WAITING);
        assertThat(beaWatching.waitingOnPlayerIds()).containsExactly(ana.playerId());
        assertThat(beaWatching.round()).as("a spectator is never handed the mystery song").isNull();

        assertThat(answer(ana, true).resolved()).isTrue();
        assertThat(gameStateService.stateFor(ana.gameId(), null, bea.playerToken()).phase()).isEqualTo(GamePhase.REVEAL);
    }

    // ------------------------------------------------------------------ housekeeping

    @Test
    void abandonedOnlineRoomsAreClosedButSoloRunsAreLeftAlone() {
        RoomJoinedResponse lobby = openRoom("Ana");
        var solo = gameService.createGame(new CreateGameRequest(
                GameMode.SOLO, List.of("Solo"), new GameSettingsRequest(null, null, null, null, Difficulty.NORMAL, 3, null)));

        Instant later = Instant.now().plusSeconds(3600);
        List<UUID> abandoned = gameService.findAbandonedOnlineGames(later, later);
        assertThat(abandoned).contains(lobby.gameId()).doesNotContain(solo.id());

        assertThat(gameService.findAbandonedOnlineGames(Instant.now().minusSeconds(3600), Instant.now().minusSeconds(3600))).isEmpty();

        gameService.closeAbandoned(lobby.gameId());
        assertThat(gameRepository.findById(lobby.gameId()).orElseThrow().getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(eventTypes()).contains(GameEventType.GAME_FINISHED);
        assertThatThrownBy(() -> roomService.lookup(lobby.roomCode())).isInstanceOf(RoomNotFoundException.class);
    }
}
