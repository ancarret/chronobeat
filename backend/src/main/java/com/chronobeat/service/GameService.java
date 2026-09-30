package com.chronobeat.service;

import com.chronobeat.config.GameProperties;
import com.chronobeat.domain.Difficulty;
import com.chronobeat.domain.Game;
import com.chronobeat.domain.GameMode;
import com.chronobeat.domain.GamePlayer;
import com.chronobeat.domain.GameRound;
import com.chronobeat.domain.GameSettings;
import com.chronobeat.domain.GameStatus;
import com.chronobeat.domain.RoundStatus;
import com.chronobeat.domain.Song;
import com.chronobeat.domain.TimelineEntry;
import com.chronobeat.dto.game.AnswerRequest;
import com.chronobeat.dto.game.CreateGameRequest;
import com.chronobeat.dto.game.GameResponse;
import com.chronobeat.dto.game.GameResultsResponse;
import com.chronobeat.dto.game.GameSettingsRequest;
import com.chronobeat.dto.game.PlayerResponse;
import com.chronobeat.dto.game.RoundPendingResponse;
import com.chronobeat.dto.game.RoundResultResponse;
import com.chronobeat.dto.game.StartGameResponse;
import com.chronobeat.dto.game.TimelineEntryResponse;
import com.chronobeat.exception.GameNotFoundException;
import com.chronobeat.exception.InvalidGameStateException;
import com.chronobeat.exception.InvalidPlacementException;
import com.chronobeat.exception.RoundAlreadyResolvedException;
import com.chronobeat.exception.RoundNotFoundException;
import com.chronobeat.mapper.GameMapper;
import com.chronobeat.repository.GameRepository;
import com.chronobeat.repository.GameRoundRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the full game lifecycle. This is the only class allowed to
 * mutate {@link Game}/{@link GamePlayer}/{@link GameRound} state; everything
 * that decides *how* to select a song, validate a placement, or score an
 * answer is delegated to a focused collaborator so each rule stays unit-testable
 * in isolation.
 */
@Service
public class GameService {

    private final GameRepository gameRepository;
    private final GameRoundRepository gameRoundRepository;
    private final SongSelectionService songSelectionService;
    private final TimelineValidationService timelineValidationService;
    private final ScoringService scoringService;
    private final GameMapper gameMapper;
    private final GameProperties gameProperties;

    public GameService(
            GameRepository gameRepository,
            GameRoundRepository gameRoundRepository,
            SongSelectionService songSelectionService,
            TimelineValidationService timelineValidationService,
            ScoringService scoringService,
            GameMapper gameMapper,
            GameProperties gameProperties) {
        this.gameRepository = gameRepository;
        this.gameRoundRepository = gameRoundRepository;
        this.songSelectionService = songSelectionService;
        this.timelineValidationService = timelineValidationService;
        this.scoringService = scoringService;
        this.gameMapper = gameMapper;
        this.gameProperties = gameProperties;
    }

    @Transactional
    public GameResponse createGame(CreateGameRequest request) {
        validatePlayerCountForMode(request.mode(), request.playerNames());

        GameSettingsRequest settingsRequest = request.settings();
        int maxLives = settingsRequest.maxLives() != null ? settingsRequest.maxLives() : gameProperties.getDefaultMaxLives();
        Difficulty difficulty = settingsRequest.difficulty() != null ? settingsRequest.difficulty() : Difficulty.NORMAL;
        GameSettings settings = new GameSettings(
                settingsRequest.market(), settingsRequest.genre(), settingsRequest.yearFrom(), settingsRequest.yearTo(),
                difficulty, maxLives, settingsRequest.maxRounds());

        Game game = new Game(request.mode(), settings);
        int order = 0;
        for (String name : request.playerNames()) {
            game.addPlayer(new GamePlayer(name.trim(), order++, maxLives));
        }
        // saveAndFlush (not save): @CreationTimestamp/@UpdateTimestamp are only populated
        // on the in-memory entity once the insert actually flushes, and we map the
        // response from this same instance immediately afterward.
        gameRepository.saveAndFlush(game);
        return gameMapper.toGameResponse(game);
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
        Game game = getGameOrThrow(gameId);
        game.start();
        GameRound round = createRoundForPlayer(game, game.getPlayers().get(0));
        gameRoundRepository.save(round);
        return new StartGameResponse(gameMapper.toGameResponse(game), gameMapper.toRoundPendingResponse(round));
    }

    private GameRound createRoundForPlayer(Game game, GamePlayer player) {
        boolean anchor = player.getTimeline().isEmpty();
        game.incrementRoundNumber();
        Song song = songSelectionService.selectNextSong(game, player);
        return new GameRound(game, player, song, game.getCurrentRoundNumber(), anchor);
    }

    @Transactional(readOnly = true)
    public RoundPendingResponse getCurrentRound(UUID gameId) {
        GameRound round = gameRoundRepository
                .findByGameIdAndStatus(gameId, RoundStatus.PENDING)
                .orElseThrow(() -> new InvalidGameStateException("No pending round for this game right now"));
        return gameMapper.toRoundPendingResponse(round);
    }

    @Transactional
    public RoundResultResponse submitAnswer(UUID gameId, UUID roundId, AnswerRequest request) {
        Game game = getGameOrThrow(gameId);
        GameRound round = gameRoundRepository.findById(roundId).orElseThrow(() -> new RoundNotFoundException(roundId));
        if (!round.getGame().getId().equals(gameId)) {
            throw new RoundNotFoundException(roundId);
        }
        if (round.getStatus() == RoundStatus.RESOLVED) {
            throw new RoundAlreadyResolvedException(roundId);
        }

        GamePlayer player = round.getGamePlayer();
        Song song = round.getSong();
        int mysteryYear = song.getEffectiveYear();

        boolean correct;
        Integer resolvedPosition;
        Set<Integer> validPositions;

        if (round.isAnchorRound()) {
            correct = true;
            resolvedPosition = 0;
            validPositions = Set.of(0);
            insertIntoTimeline(player, song, 0, round.getRoundNumber());
        } else {
            List<Integer> timelineYears = sortedTimelineYears(player);
            int size = timelineYears.size();
            Integer submitted = request.insertPosition();
            if (submitted == null || submitted < 0 || submitted > size) {
                throw new InvalidPlacementException("insertPosition must be between 0 and " + size + " for this timeline");
            }
            var result = timelineValidationService.validate(timelineYears, submitted, mysteryYear);
            correct = result.correct();
            validPositions = result.validIndices();
            resolvedPosition = submitted;

            if (correct) {
                insertIntoTimeline(player, song, submitted, round.getRoundNumber());
                int newStreak = player.getCurrentStreak() + 1;
                player.recordCorrectAnswer(scoringService.calculatePoints(newStreak));
            } else {
                player.recordIncorrectAnswer();
            }
        }

        Boolean guessCorrect = null;
        if (request.guessedSongId() != null) {
            boolean songMatches = request.guessedSongId().equals(song.getId());
            boolean yearMatches = request.guessedYear() != null && request.guessedYear() == mysteryYear;
            guessCorrect = songMatches && yearMatches;
            if (guessCorrect) {
                player.gainExtraLife();
            }
        }

        round.resolve(resolvedPosition, correct);
        gameRoundRepository.save(round);

        if (game.shouldFinish()) {
            game.finish();
        }

        return new RoundResultResponse(
                round.getId(),
                correct,
                round.isAnchorRound(),
                resolvedPosition,
                validPositions,
                guessCorrect,
                gameMapper.toSongRevealResponse(song),
                gameMapper.toPlayerResponse(player),
                sortedTimeline(player),
                game.getStatus());
    }

    @Transactional
    public RoundPendingResponse nextRound(UUID gameId) {
        Game game = getGameOrThrow(gameId);
        if (game.getStatus() != GameStatus.ACTIVE) {
            throw new InvalidGameStateException("Game is not active");
        }
        if (gameRoundRepository.findByGameIdAndStatus(gameId, RoundStatus.PENDING).isPresent()) {
            throw new InvalidGameStateException("The current round has not been answered yet");
        }
        GameRound lastRound = gameRoundRepository
                .findTopByGameIdOrderByRoundNumberDesc(gameId)
                .orElseThrow(() -> new InvalidGameStateException("Game has no rounds yet; call start first"));

        GamePlayer nextPlayer = nextActivePlayer(game, lastRound.getGamePlayer());
        GameRound round = createRoundForPlayer(game, nextPlayer);
        gameRoundRepository.save(round);
        return gameMapper.toRoundPendingResponse(round);
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

    @Transactional(readOnly = true)
    public GameResponse getGame(UUID gameId) {
        return gameMapper.toGameResponse(getGameOrThrow(gameId));
    }

    @Transactional(readOnly = true)
    public List<TimelineEntryResponse> getTimeline(UUID gameId, UUID playerId) {
        Game game = getGameOrThrow(gameId);
        GamePlayer player = resolvePlayer(game, playerId);
        return sortedTimeline(player);
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
        GamePlayer winner = game.getPlayers().stream().max(Comparator.comparingInt(GamePlayer::getScore)).orElse(null);
        return new GameResultsResponse(
                game.getId(), game.getStatus(), game.getCurrentRoundNumber(), players, winner != null ? winner.getId() : null);
    }

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

    private List<TimelineEntryResponse> sortedTimeline(GamePlayer player) {
        return player.getTimeline().stream()
                .sorted(Comparator.comparingInt(TimelineEntry::getPosition))
                .map(gameMapper::toTimelineEntryResponse)
                .toList();
    }

    private Game getGameOrThrow(UUID gameId) {
        return gameRepository.findWithPlayersById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
    }
}
