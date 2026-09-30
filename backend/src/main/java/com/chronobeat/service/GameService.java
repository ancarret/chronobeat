package com.chronobeat.service;

import com.chronobeat.config.GameProperties;
import com.chronobeat.domain.Difficulty;
import com.chronobeat.domain.Game;
import com.chronobeat.domain.GameMode;
import com.chronobeat.domain.GamePlayer;
import com.chronobeat.domain.GameRound;
import com.chronobeat.domain.GameSettings;
import com.chronobeat.domain.GameStatus;
import com.chronobeat.domain.PlayStyle;
import com.chronobeat.domain.Profile;
import com.chronobeat.domain.RoundStatus;
import com.chronobeat.domain.Song;
import com.chronobeat.domain.TimelineEntry;
import com.chronobeat.dto.game.AnswerOutcomeResponse;
import com.chronobeat.dto.game.AnswerRequest;
import com.chronobeat.dto.game.CreateGameRequest;
import com.chronobeat.dto.game.GameResponse;
import com.chronobeat.dto.game.GameResultsResponse;
import com.chronobeat.dto.game.GameSettingsRequest;
import com.chronobeat.dto.game.PlayerResponse;
import com.chronobeat.dto.game.RecordsBrokenResponse;
import com.chronobeat.dto.game.RoundPendingResponse;
import com.chronobeat.dto.game.StartGameResponse;
import com.chronobeat.dto.game.TimelineEntryResponse;
import com.chronobeat.event.GameEventType;
import com.chronobeat.event.GameEvents;
import com.chronobeat.exception.GameNotFoundException;
import com.chronobeat.exception.InvalidGameStateException;
import com.chronobeat.exception.InvalidPlacementException;
import com.chronobeat.exception.RoundAlreadyLockedException;
import com.chronobeat.exception.RoundAlreadyResolvedException;
import com.chronobeat.exception.RoundNotFoundException;
import com.chronobeat.mapper.GameMapper;
import com.chronobeat.repository.GameRepository;
import com.chronobeat.repository.GameRoundRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the full game lifecycle. This is the only class allowed to
 * mutate {@link Game}/{@link GamePlayer}/{@link GameRound} state; everything
 * that decides *how* to select a song, validate a placement, or score an
 * answer is delegated to a focused collaborator so each rule stays unit-testable
 * in isolation.
 *
 * <p>Every answer follows the same path whatever the play style: the player's round is
 * <em>locked</em>, and once no round of that round number is still pending the whole table is
 * <em>resolved</em> together. A turn-based round has a single player, so it resolves at once; a
 * shared-song round waits for everybody, which is what guarantees nothing about correctness
 * leaks before the last player has committed.
 *
 * <p>All state-changing calls first take a row lock on the game, so simultaneous requests
 * (two players locking in at the same instant, a timeout racing an answer) are handled one at a time.
 */
@Service
public class GameService {

    /** Online reveals stay up at least this long so a fast clicker can't skip everybody else's. */
    private static final int MIN_ONLINE_REVEAL_SECONDS = 3;

    /** Slack on top of a round's time limit to absorb network latency before a player is timed out. */
    private static final int TIMEOUT_GRACE_SECONDS = 2;

    private final GameRepository gameRepository;
    private final GameRoundRepository gameRoundRepository;
    private final SongSelectionService songSelectionService;
    private final TimelineValidationService timelineValidationService;
    private final ScoringService scoringService;
    private final GameMapper gameMapper;
    private final GameProperties gameProperties;
    private final ProfileStatsService profileStatsService;
    private final PlayerAuthenticator playerAuthenticator;
    private final GameEvents gameEvents;

    public GameService(
            GameRepository gameRepository,
            GameRoundRepository gameRoundRepository,
            SongSelectionService songSelectionService,
            TimelineValidationService timelineValidationService,
            ScoringService scoringService,
            GameMapper gameMapper,
            GameProperties gameProperties,
            ProfileStatsService profileStatsService,
            PlayerAuthenticator playerAuthenticator,
            GameEvents gameEvents) {
        this.gameRepository = gameRepository;
        this.gameRoundRepository = gameRoundRepository;
        this.songSelectionService = songSelectionService;
        this.timelineValidationService = timelineValidationService;
        this.scoringService = scoringService;
        this.gameMapper = gameMapper;
        this.gameProperties = gameProperties;
        this.profileStatsService = profileStatsService;
        this.playerAuthenticator = playerAuthenticator;
        this.gameEvents = gameEvents;
    }

    // ------------------------------------------------------------------ create / start

    @Transactional
    public GameResponse createGame(CreateGameRequest request) {
        return createGame(request, null);
    }

    /** @param profile the authenticated caller, or null for a guest game whose results aren't tracked */
    @Transactional
    public GameResponse createGame(CreateGameRequest request, Profile profile) {
        if (request.mode() == GameMode.ONLINE_MULTIPLAYER) {
            throw new InvalidGameStateException("Online games are created as rooms, not through this endpoint");
        }
        validatePlayerCountForMode(request.mode(), request.playerNames());
        int profileIndex = profileIndexFor(request, profile);

        Game game = new Game(request.mode(), settingsFrom(request.settings(), null));
        int order = 0;
        for (String name : request.playerNames()) {
            GamePlayer player = new GamePlayer(name.trim(), order, game.getSettings().getMaxLives());
            if (order == profileIndex) {
                player.linkProfile(profile);
            }
            game.addPlayer(player);
            order++;
        }
        // saveAndFlush (not save): @CreationTimestamp/@UpdateTimestamp are only populated
        // on the in-memory entity once the insert actually flushes, and we map the
        // response from this same instance immediately afterward.
        gameRepository.saveAndFlush(game);
        return gameMapper.toGameResponse(game);
    }

    /**
     * Turns client-supplied settings into the stored rules, applying server defaults.
     *
     * @param defaultAnswerSeconds the time limit to use when the client didn't ask for one (null = untimed)
     */
    GameSettings settingsFrom(GameSettingsRequest request, Integer defaultAnswerSeconds) {
        int maxLives = request.maxLives() != null ? request.maxLives() : gameProperties.getDefaultMaxLives();
        Difficulty difficulty = request.difficulty() != null ? request.difficulty() : Difficulty.NORMAL;
        PlayStyle playStyle = request.playStyle() != null ? request.playStyle() : PlayStyle.TURN_BASED;
        Integer answerSeconds = request.answerSeconds() != null ? request.answerSeconds() : defaultAnswerSeconds;
        return new GameSettings(
                request.market(), request.genre(), request.yearFrom(), request.yearTo(), difficulty, maxLives,
                request.maxRounds(), playStyle, request.targetTimelineSize(), answerSeconds);
    }

    /** Index of the player that belongs to {@code profile}, or -1 when the game is a guest game. */
    private int profileIndexFor(CreateGameRequest request, Profile profile) {
        if (profile == null) {
            return -1;
        }
        Integer requested = request.profilePlayerIndex();
        if (requested == null) {
            return request.mode() == GameMode.SOLO ? 0 : -1;
        }
        if (requested >= request.playerNames().size()) {
            throw new InvalidGameStateException("profilePlayerIndex must refer to one of the " + request.playerNames().size() + " players");
        }
        return requested;
    }

    private void validatePlayerCountForMode(GameMode mode, List<String> names) {
        if (mode == GameMode.SOLO && names.size() != 1) {
            throw new InvalidGameStateException("Solo mode requires exactly one player name");
        }
        if (mode == GameMode.LOCAL_MULTIPLAYER && names.size() < 2) {
            throw new InvalidGameStateException("Local multiplayer requires at least two player names");
        }
    }

    @Transactional
    public StartGameResponse startGame(UUID gameId) {
        return startGame(gameId, null);
    }

    /** @param playerToken the host's token; only checked for online games */
    @Transactional
    public StartGameResponse startGame(UUID gameId, String playerToken) {
        Game game = lockGame(gameId);
        playerAuthenticator.requireHost(game, playerToken);
        if (game.getStatus() != GameStatus.CREATED) {
            throw new InvalidGameStateException("Game already started");
        }
        if (game.isOnline() && game.getPlayers().size() < 2) {
            throw new InvalidGameStateException("Wait for at least one more player before starting");
        }
        game.start();
        List<GameRound> dealt = dealRound(game, null);
        gameEvents.publish(gameId, GameEventType.GAME_STARTED);
        return new StartGameResponse(gameMapper.toGameResponse(game), gameMapper.toRoundPendingResponse(dealt.get(0)));
    }

    /**
     * Deals the next round: one round for the next active player in a turn-based game, or one round
     * on the same song for every active player in a shared-song game.
     *
     * @param previousTurnPlayer whose turn came before (turn-based only); null to begin with the first seat
     */
    private List<GameRound> dealRound(Game game, GamePlayer previousTurnPlayer) {
        game.incrementRoundNumber();
        int number = game.getCurrentRoundNumber();

        List<GameRound> dealt = new ArrayList<>();
        if (game.isSharedSongs()) {
            Song song = songSelectionService.selectSharedSong(game);
            for (GamePlayer player : game.getPlayers()) {
                if (!player.isEliminated()) {
                    dealt.add(new GameRound(game, player, song, number, player.getTimeline().isEmpty()));
                }
            }
        } else {
            GamePlayer player = previousTurnPlayer == null ? game.getPlayers().get(0) : nextActivePlayer(game, previousTurnPlayer);
            Song song = songSelectionService.selectNextSong(game, player);
            dealt.add(new GameRound(game, player, song, number, player.getTimeline().isEmpty()));
        }
        return gameRoundRepository.saveAll(dealt);
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public GameResponse getGame(UUID gameId) {
        Game game = getGameOrThrow(gameId);
        return gameMapper.toGameResponse(game, lockedPlayerIds(game));
    }

    /** The first unanswered round of the game (in a turn-based game, the only one). */
    @Transactional(readOnly = true)
    public RoundPendingResponse getCurrentRound(UUID gameId) {
        return getCurrentRound(gameId, null);
    }

    /** The unanswered round of one player of a shared-song game; {@code playerId} null means "the first one waiting". */
    @Transactional(readOnly = true)
    public RoundPendingResponse getCurrentRound(UUID gameId, UUID playerId) {
        return gameRoundRepository.findAllByGameIdAndStatus(gameId, RoundStatus.PENDING).stream()
                .filter(r -> playerId == null || r.getGamePlayer().getId().equals(playerId))
                .min(Comparator.comparingInt((GameRound r) -> r.getGamePlayer().getPlayerOrder()))
                .map(gameMapper::toRoundPendingResponse)
                .orElseThrow(() -> new InvalidGameStateException("No pending round for this game right now"));
    }

    /** Players who have locked in for the round in progress (only meaningful while a shared round waits on others). */
    Set<UUID> lockedPlayerIds(Game game) {
        if (game.getStatus() != GameStatus.ACTIVE || !game.isSharedSongs()) {
            return Set.of();
        }
        return gameRoundRepository.findRound(game.getId(), game.getCurrentRoundNumber()).stream()
                .filter(r -> r.getStatus() == RoundStatus.LOCKED)
                .map(r -> r.getGamePlayer().getId())
                .collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------ answering

    /** Shared-device convenience: no player token is needed when everybody uses the same screen. */
    @Transactional
    public AnswerOutcomeResponse submitAnswer(UUID gameId, UUID roundId, AnswerRequest request) {
        return submitAnswer(gameId, roundId, request, null);
    }

    /**
     * Locks in a placement. In a turn-based game (and whenever this was the last answer outstanding)
     * the round resolves immediately and the outcome carries this player's result.
     *
     * @param playerToken the answering player's token; only checked for online games
     */
    @Transactional
    public AnswerOutcomeResponse submitAnswer(UUID gameId, UUID roundId, AnswerRequest request, String playerToken) {
        Game game = lockGame(gameId);
        GameRound round = gameRoundRepository.findByIdWithSong(roundId).orElseThrow(() -> new RoundNotFoundException(roundId));
        if (!round.getGame().getId().equals(gameId)) {
            throw new RoundNotFoundException(roundId);
        }
        playerAuthenticator.requireOwnRound(game, round, playerToken);
        if (round.getStatus() == RoundStatus.RESOLVED) {
            throw new RoundAlreadyResolvedException(roundId);
        }
        if (round.getStatus() == RoundStatus.LOCKED) {
            throw new RoundAlreadyLockedException(roundId);
        }

        // The anchor is placed automatically, so there is no position to validate or store.
        Integer position = round.isAnchorRound() ? null : validatedPosition(round.getGamePlayer(), request.insertPosition());
        round.lock(position, request.guessedSongId(), request.guessedYear());
        return settle(game, round);
    }

    /**
     * After a round was locked: resolve the whole table if nobody is left to answer, otherwise tell
     * clients that one more answer is in.
     */
    private AnswerOutcomeResponse settle(Game game, GameRound justLocked) {
        List<GameRound> table = gameRoundRepository.findRound(game.getId(), justLocked.getRoundNumber());
        int waiting = (int) table.stream().filter(r -> r.getStatus() == RoundStatus.PENDING).count();
        if (waiting > 0) {
            gameEvents.publish(game.getId(), GameEventType.ANSWER_LOCKED);
            return new AnswerOutcomeResponse(false, waiting, null);
        }

        table.forEach(this::applyAnswer);
        if (game.shouldFinish()) {
            game.finish();
            gameEvents.publish(game.getId(), GameEventType.GAME_FINISHED);
        } else {
            gameEvents.publish(game.getId(), GameEventType.ROUND_RESOLVED);
        }
        return new AnswerOutcomeResponse(true, 0, gameMapper.toRoundResult(justLocked, game.getStatus()));
    }

    /** Judges one locked round: placement, timeline, score, streak, lives and the optional song guess. */
    private void applyAnswer(GameRound round) {
        GamePlayer player = round.getGamePlayer();
        Song song = round.getSong();
        int mysteryYear = song.getEffectiveYear();

        boolean correct;
        Set<Integer> validPositions;

        if (round.isAnchorRound()) {
            correct = true;
            validPositions = Set.of(0);
            insertIntoTimeline(player, song, 0, round.getRoundNumber());
        } else {
            List<Integer> timelineYears = sortedTimelineYears(player);
            Integer submitted = round.getSubmittedPosition();
            if (submitted == null) {
                // Timed out: no placement at all counts as a miss, and the reveal still shows where it belonged.
                correct = false;
                validPositions = timelineValidationService.computeValidIndices(timelineYears, mysteryYear);
                player.recordIncorrectAnswer();
            } else {
                var result = timelineValidationService.validate(timelineYears, submitted, mysteryYear);
                correct = result.correct();
                validPositions = result.validIndices();
                if (correct) {
                    insertIntoTimeline(player, song, submitted, round.getRoundNumber());
                    player.recordCorrectAnswer(scoringService.calculatePoints(player.getCurrentStreak() + 1));
                } else {
                    player.recordIncorrectAnswer();
                }
            }
        }

        Boolean guessCorrect = null;
        if (round.getGuessedSongId() != null) {
            boolean songMatches = round.getGuessedSongId().equals(song.getId());
            boolean yearMatches = round.getGuessedYear() != null && round.getGuessedYear() == mysteryYear;
            guessCorrect = songMatches && yearMatches;
            if (guessCorrect) {
                player.gainExtraLife();
            }
        }

        round.resolve(correct, validPositions, guessCorrect);
        gameRoundRepository.save(round);
    }

    private int validatedPosition(GamePlayer player, Integer submitted) {
        int size = player.getTimeline().size();
        if (submitted == null || submitted < 0 || submitted > size) {
            throw new InvalidPlacementException("insertPosition must be between 0 and " + size + " for this timeline");
        }
        return submitted;
    }

    // ------------------------------------------------------------------ timeouts

    /** Games with at least one unanswered round that has run past its time limit. */
    @Transactional(readOnly = true)
    public Set<UUID> findGamesWithOverdueRounds() {
        Instant now = Instant.now();
        return gameRoundRepository.findPendingRoundsOfTimedGames().stream()
                .filter(r -> isOverdue(r, now))
                .map(r -> r.getGame().getId())
                .collect(Collectors.toSet());
    }

    private boolean isOverdue(GameRound round, Instant now) {
        int limit = round.getGame().getSettings().getAnswerSeconds() + TIMEOUT_GRACE_SECONDS;
        return round.getCreatedAt().plusSeconds(limit).isBefore(now);
    }

    /**
     * Gives up on players who ran out the clock: their round is locked with no placement, which
     * resolves as a miss. May in turn resolve the whole round, or end the game.
     */
    @Transactional
    public void expireOverdueRounds(UUID gameId) {
        Game game = lockGame(gameId);
        if (game.getStatus() != GameStatus.ACTIVE) {
            return;
        }
        Instant now = Instant.now();
        GameRound lastExpired = null;
        for (GameRound round : gameRoundRepository.findRound(gameId, game.getCurrentRoundNumber())) {
            if (round.getStatus() == RoundStatus.PENDING && isOverdue(round, now)) {
                round.lock(null, null, null);
                lastExpired = round;
            }
        }
        if (lastExpired != null) {
            settle(game, lastExpired);
        }
    }

    // ------------------------------------------------------------------ abandoned rooms

    /**
     * Online games nobody has touched for a long while: rooms never started before {@code lobbyCutoff},
     * running games idle since {@code activeCutoff}. Solo and shared-device games are never included;
     * a run left open on somebody's phone is theirs to resume.
     */
    @Transactional(readOnly = true)
    public List<UUID> findAbandonedOnlineGames(Instant lobbyCutoff, Instant activeCutoff) {
        List<Game> stale = new ArrayList<>(gameRepository.findByStatusInAndUpdatedAtBefore(List.of(GameStatus.CREATED), lobbyCutoff));
        stale.addAll(gameRepository.findByStatusInAndUpdatedAtBefore(List.of(GameStatus.ACTIVE), activeCutoff));
        return stale.stream().filter(Game::isOnline).map(Game::getId).toList();
    }

    /** Ends an abandoned online game so its room code is released. */
    @Transactional
    public void closeAbandoned(UUID gameId) {
        Game game = lockGame(gameId);
        if (game.getStatus() != GameStatus.FINISHED) {
            game.finish();
            gameEvents.publish(gameId, GameEventType.GAME_FINISHED);
        }
    }

    // ------------------------------------------------------------------ advancing

    /** Convenience for shared-device play and tests: deals the next round and returns the first pending one. */
    @Transactional
    public RoundPendingResponse nextRound(UUID gameId) {
        advance(gameId, null, null);
        return getCurrentRound(gameId);
    }

    /**
     * Deals the next round once the current one is fully resolved.
     *
     * <p>Idempotent for the "everyone clicks Next" case: {@code afterRound} is the round the caller just
     * watched, and if the game already moved past it (another player got there first) this does nothing
     * instead of skipping a round.
     *
     * @param afterRound the round number the caller has revealed, or null to just advance from wherever the game is
     * @param playerToken the caller's token; only checked for online games
     */
    @Transactional
    public void advance(UUID gameId, Integer afterRound, String playerToken) {
        Game game = lockGame(gameId);
        playerAuthenticator.authenticate(game, playerToken);
        if (game.getStatus() != GameStatus.ACTIVE) {
            throw new InvalidGameStateException("Game is not active");
        }
        int latest = game.getCurrentRoundNumber();
        if (afterRound != null && afterRound < latest) {
            return;
        }

        List<GameRound> table = gameRoundRepository.findRound(gameId, latest);
        if (table.isEmpty()) {
            throw new InvalidGameStateException("Game has no rounds yet; call start first");
        }
        if (table.stream().anyMatch(r -> r.getStatus() != RoundStatus.RESOLVED)) {
            throw new InvalidGameStateException("The current round has not been answered yet");
        }
        if (game.isOnline() && isRevealStillOnScreen(table)) {
            throw new InvalidGameStateException("The reveal is still on screen; give everyone a moment");
        }

        dealRound(game, table.get(0).getGamePlayer());
        gameEvents.publish(gameId, GameEventType.ROUND_DEALT);
    }

    private boolean isRevealStillOnScreen(List<GameRound> resolvedTable) {
        Instant shownSince = resolvedTable.stream()
                .map(GameRound::getResolvedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return shownSince != null && shownSince.plusSeconds(MIN_ONLINE_REVEAL_SECONDS).isAfter(Instant.now());
    }

    private GamePlayer nextActivePlayer(Game game, GamePlayer current) {
        List<GamePlayer> players = game.getPlayers();
        int startIndex = players.indexOf(current);
        for (int offset = 1; offset <= players.size(); offset++) {
            GamePlayer candidate = players.get((startIndex + offset) % players.size());
            if (!candidate.isEliminated()) {
                return candidate;
            }
        }
        throw new InvalidGameStateException("No active players remain");
    }

    // ------------------------------------------------------------------ timeline / results

    @Transactional(readOnly = true)
    public List<TimelineEntryResponse> getTimeline(UUID gameId, UUID playerId) {
        Game game = getGameOrThrow(gameId);
        GamePlayer player = resolvePlayer(game, playerId);
        return gameMapper.toTimeline(player);
    }

    private GamePlayer resolvePlayer(Game game, UUID playerId) {
        if (playerId == null) {
            return game.getPlayers().get(0);
        }
        return game.getPlayers().stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new InvalidGameStateException("Player " + playerId + " is not part of game " + game.getId()));
    }

    @Transactional(readOnly = true)
    public GameResultsResponse getResults(UUID gameId) {
        Game game = getGameOrThrow(gameId);
        if (game.getStatus() != GameStatus.FINISHED) {
            throw new InvalidGameStateException("Game is not finished yet");
        }
        List<PlayerResponse> players = game.getPlayers().stream().map(gameMapper::toPlayerResponse).toList();
        // No winner means a genuine tie (identical on every tie-break), which the client presents as such.
        UUID winnerId = game.determineWinner().map(GamePlayer::getId).orElse(null);
        List<RecordsBrokenResponse> records = game.getPlayers().stream()
                .map(profileStatsService::recordsBrokenBy)
                .filter(Objects::nonNull)
                .toList();
        return new GameResultsResponse(
                game.getId(), game.getStatus(), game.getCurrentRoundNumber(), players, winnerId, records);
    }

    // ------------------------------------------------------------------ helpers

    private void insertIntoTimeline(GamePlayer player, Song song, int position, int roundNumber) {
        for (TimelineEntry entry : player.getTimeline()) {
            if (entry.getPosition() >= position) {
                entry.setPosition(entry.getPosition() + 1);
            }
        }
        player.addToTimeline(new TimelineEntry(song, position, roundNumber));
    }

    private List<Integer> sortedTimelineYears(GamePlayer player) {
        return player.getTimeline().stream()
                .sorted(Comparator.comparingInt(TimelineEntry::getPosition))
                .map(entry -> entry.getSong().getEffectiveYear())
                .toList();
    }

    private Game getGameOrThrow(UUID gameId) {
        return gameRepository.findWithPlayersById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
    }

    /** Loads the game with its players after taking the row lock that serialises state changes. */
    private Game lockGame(UUID gameId) {
        gameRepository.lockById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        return getGameOrThrow(gameId);
    }
}
