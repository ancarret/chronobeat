package com.chronobeat.mapper;

import com.chronobeat.config.GameProperties;
import com.chronobeat.domain.Game;
import com.chronobeat.domain.GamePlayer;
import com.chronobeat.domain.GameRound;
import com.chronobeat.domain.GameSettings;
import com.chronobeat.domain.Song;
import com.chronobeat.domain.TimelineEntry;
import com.chronobeat.dto.game.GameResponse;
import com.chronobeat.dto.game.GameSettingsResponse;
import com.chronobeat.dto.game.PlayerResponse;
import com.chronobeat.dto.game.RoundPendingResponse;
import com.chronobeat.dto.game.SongRevealResponse;
import com.chronobeat.dto.game.TimelineEntryResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts JPA entities into the DTOs actually sent over the wire, keeping controllers thin. */
@Component
public class GameMapper {

    private final GameProperties gameProperties;

    public GameMapper(GameProperties gameProperties) {
        this.gameProperties = gameProperties;
    }

    public GameResponse toGameResponse(Game game) {
        List<PlayerResponse> players = game.getPlayers().stream().map(this::toPlayerResponse).toList();
        return new GameResponse(
                game.getId(), game.getMode(), game.getStatus(), toSettingsResponse(game.getSettings()), game.getCurrentRoundNumber(), players, game.getCreatedAt());
    }

    public GameSettingsResponse toSettingsResponse(GameSettings settings) {
        return new GameSettingsResponse(
                settings.getMarket(), settings.getGenre(), settings.getYearFrom(), settings.getYearTo(),
                settings.getDifficulty(), settings.getMaxLives(), settings.getMaxRounds());
    }

    public PlayerResponse toPlayerResponse(GamePlayer player) {
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
                player.getTimeline().size());
    }

    public TimelineEntryResponse toTimelineEntryResponse(TimelineEntry entry) {
        Song song = entry.getSong();
        return new TimelineEntryResponse(
                song.getId(), song.getTitle(), song.getArtist(), song.getAlbum(), song.getEffectiveYear(), song.getArtworkUrl(), song.getGenre(), entry.getPosition());
    }

    public List<TimelineEntryResponse> toTimeline(GamePlayer player) {
        return player.getTimeline().stream().map(this::toTimelineEntryResponse).toList();
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
                player.getLivesRemaining());
    }

    public SongRevealResponse toSongRevealResponse(Song song) {
        return new SongRevealResponse(song.getId(), song.getTitle(), song.getArtist(), song.getAlbum(), song.getEffectiveYear(), song.getArtworkUrl(), song.getGenre());
    }
}
