package com.chronobeat.mapper;

import com.chronobeat.config.GameProperties;
import com.chronobeat.domain.Game;
import com.chronobeat.domain.GamePlayer;
import com.chronobeat.domain.GameRound;
import com.chronobeat.domain.GameSettings;
import com.chronobeat.domain.GameStatus;
import com.chronobeat.domain.Song;
import com.chronobeat.domain.TimelineEntry;
import com.chronobeat.dto.game.GameResponse;
import com.chronobeat.dto.game.GameSettingsResponse;
import com.chronobeat.dto.game.PlayerResponse;
import com.chronobeat.dto.game.RoundPendingResponse;
import com.chronobeat.dto.game.RoundResultResponse;
import com.chronobeat.dto.game.SongRevealResponse;
import com.chronobeat.dto.game.TimelineEntryResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Converts JPA entities into the DTOs actually sent over the wire, keeping controllers thin. */
@Component
public class GameMapper {

    private final GameProperties gameProperties;

    public GameMapper(GameProperties gameProperties) {
        this.gameProperties = gameProperties;
    }

    public GameResponse toGameResponse(Game game) {
        return toGameResponse(game, Set.of());
    }

    /** @param answeredPlayerIds players who have locked in an answer for the round in progress */
    public GameResponse toGameResponse(Game game, Set<UUID> answeredPlayerIds) {
        List<PlayerResponse> players = game.getPlayers().stream()
                .map(p -> toPlayerResponse(p, answeredPlayerIds.contains(p.getId())))
                .toList();
        return new GameResponse(
                game.getId(), game.getMode(), game.getStatus(), toSettingsResponse(game.getSettings()),
                game.getCurrentRoundNumber(), players, game.getCreatedAt(), game.getRoomCode());
    }

    public GameSettingsResponse toSettingsResponse(GameSettings settings) {
        return new GameSettingsResponse(
                settings.getMarket(), settings.getGenre(), settings.getYearFrom(), settings.getYearTo(),
                settings.getDifficulty(), settings.getMaxLives(), settings.getMaxRounds(),
                settings.getPlayStyle(), settings.getTargetTimelineSize(), settings.getAnswerSeconds());
    }

    public PlayerResponse toPlayerResponse(GamePlayer player) {
        return toPlayerResponse(player, false);
    }

    public PlayerResponse toPlayerResponse(GamePlayer player, boolean answered) {
        return new PlayerResponse(
                player.getId(),
                player.getDisplayName(),
                player.getPlayerOrder(),
                player.getScore(),
                player.getCorrectAnswers(),
                player.getIncorrectAnswers(),
                player.getAccuracy(),
                player.getCurrentStreak(),
                player.getBestStreak(),
                player.getLivesRemaining(),
                player.isEliminated(),
                player.getTimeline().size(),
                player.isHost(),
                answered);
    }

    public TimelineEntryResponse toTimelineEntryResponse(TimelineEntry entry) {
        Song song = entry.getSong();
        return new TimelineEntryResponse(
                song.getId(), song.getTitle(), song.getArtist(), song.getAlbum(), song.getEffectiveYear(), song.getArtworkUrl(), song.getGenre(), entry.getPosition());
    }

    /** The player's timeline in chronological order (in-memory inserts within a transaction aren't kept sorted). */
    public List<TimelineEntryResponse> toTimeline(GamePlayer player) {
        return player.getTimeline().stream()
                .sorted(Comparator.comparingInt(TimelineEntry::getPosition))
                .map(this::toTimelineEntryResponse)
                .toList();
    }

    public RoundPendingResponse toRoundPendingResponse(GameRound round) {
        GamePlayer player = round.getGamePlayer();
        return new RoundPendingResponse(
                round.getId(),
                round.getGame().getId(),
                player.getId(),
                player.getDisplayName(),
                round.getRoundNumber(),
                round.isAnchorRound(),
                round.getSong().getPreviewUrl(),
                gameProperties.getPreviewPlaySeconds(),
                toTimeline(player),
                player.getTimeline().size() + 1,
                player.getLivesRemaining(),
                secondsRemaining(round));
    }

    /**
     * How long a still-unanswered round has left, or null for untimed games. Computed here (server
     * clock only) so clients just count down from a duration instead of trusting their own clock.
     */
    public Integer secondsRemaining(GameRound round) {
        Integer limit = round.getGame().getSettings().getAnswerSeconds();
        if (limit == null) {
            return null;
        }
        // A round dealt in this very transaction hasn't been flushed yet, so it has no timestamp: nothing has elapsed.
        Instant dealt = round.getCreatedAt();
        long elapsed = dealt == null ? 0 : Duration.between(dealt, Instant.now()).toSeconds();
        return (int) Math.max(0, limit - elapsed);
    }

    /** A resolved round as one player experienced it; safe to send to anyone in the game. */
    public RoundResultResponse toRoundResult(GameRound round, GameStatus gameStatus) {
        GamePlayer player = round.getGamePlayer();
        return new RoundResultResponse(
                round.getId(),
                Boolean.TRUE.equals(round.getCorrect()),
                round.isAnchorRound(),
                // The anchor is always placed at index 0 even though no position is submitted for it.
                round.isAnchorRound() ? Integer.valueOf(0) : round.getSubmittedPosition(),
                round.getValidPositions(),
                round.getGuessCorrect(),
                toSongRevealResponse(round.getSong()),
                toPlayerResponse(player),
                toTimeline(player),
                gameStatus);
    }

    public SongRevealResponse toSongRevealResponse(Song song) {
        return new SongRevealResponse(song.getId(), song.getTitle(), song.getArtist(), song.getAlbum(), song.getEffectiveYear(), song.getArtworkUrl(), song.getGenre());
    }
}
