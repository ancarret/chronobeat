package com.chronobeat.service;

import com.chronobeat.config.GameProperties;
import com.chronobeat.domain.Game;
import com.chronobeat.domain.GameMode;
import com.chronobeat.domain.GamePlayer;
import com.chronobeat.domain.GameStatus;
import com.chronobeat.domain.Profile;
import com.chronobeat.dto.room.CreateRoomRequest;
import com.chronobeat.dto.room.JoinRoomRequest;
import com.chronobeat.dto.room.RoomInfoResponse;
import com.chronobeat.dto.room.RoomJoinedResponse;
import com.chronobeat.event.GameEventType;
import com.chronobeat.event.GameEvents;
import com.chronobeat.exception.GameNotFoundException;
import com.chronobeat.exception.InvalidGameStateException;
import com.chronobeat.exception.NotYourRoundException;
import com.chronobeat.exception.RoomNotFoundException;
import com.chronobeat.mapper.GameMapper;
import com.chronobeat.repository.GameRepository;
import com.chronobeat.util.SecretTokens;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Online rooms: a host opens one, friends join with a short code from their own devices, and the host
 * starts the game once everyone is in. A room is an ordinary {@link Game} in {@code CREATED} status
 * that additionally has a code and whose players each hold a secret token.
 */
@Service
public class RoomService {

    /** Consonants only: no vowels means a code can never spell a word, and no 0/O or 1/I lookalikes. */
    private static final String CODE_ALPHABET = "BCDFGHJKLMNPQRSTVWXZ";

    private static final int CODE_LENGTH = 4;
    private static final int LONG_CODE_LENGTH = 5;
    private static final int CODE_ATTEMPTS = 25;
    static final int MAX_PLAYERS = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GameRepository gameRepository;
    private final GameMapper gameMapper;
    private final GameService gameService;
    private final GameProperties gameProperties;
    private final PlayerAuthenticator playerAuthenticator;
    private final GameEvents gameEvents;

    public RoomService(
            GameRepository gameRepository,
            GameMapper gameMapper,
            GameService gameService,
            GameProperties gameProperties,
            PlayerAuthenticator playerAuthenticator,
            GameEvents gameEvents) {
        this.gameRepository = gameRepository;
        this.gameMapper = gameMapper;
        this.gameService = gameService;
        this.gameProperties = gameProperties;
        this.playerAuthenticator = playerAuthenticator;
        this.gameEvents = gameEvents;
    }

    /** @param profile the caller's profile, if they have one, so the game counts towards their records */
    @Transactional
    public RoomJoinedResponse createRoom(CreateRoomRequest request, Profile profile) {
        Game game = new Game(
                GameMode.ONLINE_MULTIPLAYER, gameService.settingsFrom(request.settings(), gameProperties.getOnlineAnswerSeconds()));
        game.assignRoomCode(newRoomCode());

        String token = SecretTokens.generate();
        GamePlayer host = seat(game, request.nickname(), profile, token);
        host.makeHost();

        gameRepository.saveAndFlush(game);
        return joined(game, host, token);
    }

    @Transactional
    public RoomJoinedResponse join(String roomCode, JoinRoomRequest request, Profile profile) {
        // Locked before the player list is read, so simultaneous joins are counted one after the other.
        Game game = gameRepository
                .lockByRoomCodeAndStatus(normalize(roomCode), GameStatus.CREATED)
                .orElseThrow(() -> new RoomNotFoundException(normalize(roomCode)));

        if (game.getPlayers().size() >= MAX_PLAYERS) {
            throw new InvalidGameStateException("This room is full (" + MAX_PLAYERS + " players)");
        }
        if (profile != null && game.getPlayers().stream().anyMatch(p -> p.getProfile() != null && p.getProfile().equals(profile))) {
            throw new InvalidGameStateException("You are already in this room");
        }

        String token = SecretTokens.generate();
        GamePlayer player = seat(game, uniqueName(game, request.nickname().trim()), profile, token);
        // flush(), not saveAndFlush(): the game is already managed, so save() would merge and persist a
        // *copy* of the new player, leaving the instance we return without an id.
        gameRepository.flush();

        gameEvents.publish(game.getId(), GameEventType.LOBBY_CHANGED);
        return joined(game, player, token);
    }

    /** Public preview shown on the join screen; reveals nothing beyond who is waiting and the rules. */
    @Transactional(readOnly = true)
    public RoomInfoResponse lookup(String roomCode) {
        Game game = openRoom(roomCode);
        String hostName = game.getPlayers().stream().filter(GamePlayer::isHost).map(GamePlayer::getDisplayName).findFirst().orElse("");
        return new RoomInfoResponse(
                game.getRoomCode(), game.getId(), game.getStatus(), hostName, game.getPlayers().size(), MAX_PLAYERS,
                gameMapper.toSettingsResponse(game.getSettings()));
    }

    /** Leaves a room that hasn't started. If the host leaves, the next player in line takes over. */
    @Transactional
    public void leave(UUID gameId, String playerToken) {
        Game game = lobby(gameId);
        GamePlayer leaving = playerAuthenticator.authenticate(game, playerToken).orElseThrow();
        game.removePlayer(leaving);

        if (game.getPlayers().isEmpty()) {
            game.finish(); // nobody left: close the room and release its code
        } else if (leaving.isHost()) {
            game.getPlayers().stream().min(Comparator.comparingInt(GamePlayer::getPlayerOrder)).orElseThrow().makeHost();
        }
        gameRepository.flush();
        gameEvents.publish(gameId, GameEventType.LOBBY_CHANGED);
    }

    /** The host removes somebody from a room that hasn't started. */
    @Transactional
    public void kick(UUID gameId, UUID targetPlayerId, String hostToken) {
        Game game = lobby(gameId);
        GamePlayer host = playerAuthenticator.authenticate(game, hostToken).orElseThrow();
        if (!host.isHost()) {
            throw new NotYourRoundException("Only the host can remove players");
        }
        GamePlayer target = game.getPlayers().stream()
                .filter(p -> p.getId().equals(targetPlayerId))
                .findFirst()
                .orElseThrow(() -> new InvalidGameStateException("That player is not in this room"));
        if (target.equals(host)) {
            throw new InvalidGameStateException("The host can't remove themselves; leave the room instead");
        }
        game.removePlayer(target);
        gameRepository.flush();
        gameEvents.publish(gameId, GameEventType.LOBBY_CHANGED);
    }

    private Game lobby(UUID gameId) {
        gameRepository.lockById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        Game game = gameRepository.findWithPlayersById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        if (!game.isOnline()) {
            throw new InvalidGameStateException("Only online rooms have a lobby");
        }
        if (game.getStatus() != GameStatus.CREATED) {
            throw new InvalidGameStateException("The game has already started");
        }
        return game;
    }

    private Game openRoom(String roomCode) {
        String code = normalize(roomCode);
        return gameRepository
                .findWithPlayersByRoomCodeAndStatus(code, GameStatus.CREATED)
                .orElseThrow(() -> new RoomNotFoundException(code));
    }

    private GamePlayer seat(Game game, String nickname, Profile profile, String token) {
        int nextOrder = game.getPlayers().stream().mapToInt(GamePlayer::getPlayerOrder).max().orElse(-1) + 1;
        GamePlayer player = new GamePlayer(nickname.trim(), nextOrder, game.getSettings().getMaxLives());
        player.secureWith(SecretTokens.hash(token));
        if (profile != null) {
            player.linkProfile(profile);
        }
        game.addPlayer(player);
        return player;
    }

    /** "Ana" -> "Ana (2)" when somebody in the room already answers to that name, so the table can tell them apart. */
    private String uniqueName(Game game, String wanted) {
        String candidate = wanted;
        int suffix = 2;
        while (nameTaken(game, candidate)) {
            candidate = wanted + " (" + suffix++ + ")";
        }
        return candidate;
    }

    private boolean nameTaken(Game game, String name) {
        return game.getPlayers().stream().anyMatch(p -> p.getDisplayName().equalsIgnoreCase(name));
    }

    private RoomJoinedResponse joined(Game game, GamePlayer player, String token) {
        return new RoomJoinedResponse(game.getId(), game.getRoomCode(), player.getId(), token, gameMapper.toGameResponse(game));
    }

    private String newRoomCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = randomCode(attempt < CODE_ATTEMPTS / 2 ? CODE_LENGTH : LONG_CODE_LENGTH);
            if (!gameRepository.existsByRoomCodeAndStatusNot(code, GameStatus.FINISHED)) {
                return code;
            }
        }
        throw new InvalidGameStateException("Couldn't allocate a room code; please try again");
    }

    private static String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    static String normalize(String roomCode) {
        return roomCode == null ? "" : roomCode.trim().toUpperCase(Locale.ROOT);
    }
}
