package com.chronobeat.service;

import com.chronobeat.config.GameProperties;
import com.chronobeat.domain.Difficulty;
import com.chronobeat.domain.Game;
import com.chronobeat.domain.GamePlayer;
import com.chronobeat.domain.GameSettings;
import com.chronobeat.domain.Song;
import com.chronobeat.exception.InsufficientCatalogException;
import com.chronobeat.repository.GameRoundRepository;
import com.chronobeat.repository.SongRepository;
import com.chronobeat.repository.SongSpecifications;
import com.chronobeat.util.RandomProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Chooses the mystery song for the next round. All filtering/exclusion happens
 * here so {@code GameService} stays free of query and randomness concerns
 * (see class-level note on {@link RandomProvider} for why selection is testable).
 */
@Service
public class SongSelectionService {

    /** Fraction of the (already filtered) candidate pool kept after difficulty-based re-ranking. */
    private static final double DIFFICULTY_BIAS_FRACTION = 0.4;

    private final SongRepository songRepository;
    private final GameRoundRepository gameRoundRepository;
    private final RandomProvider randomProvider;
    private final GameProperties gameProperties;

    public SongSelectionService(
            SongRepository songRepository,
            GameRoundRepository gameRoundRepository,
            RandomProvider randomProvider,
            GameProperties gameProperties) {
        this.songRepository = songRepository;
        this.gameRoundRepository = gameRoundRepository;
        this.randomProvider = randomProvider;
        this.gameProperties = gameProperties;
    }

    public Song selectNextSong(Game game, GamePlayer player) {
        GameSettings settings = game.getSettings();
        List<java.util.UUID> usedSongIds = gameRoundRepository.findUsedSongIdsByGameId(game.getId());

        Specification<Song> spec = Specification.allOf(
                SongSpecifications.hasPreview(),
                SongSpecifications.withMarket(settings.getMarket()),
                SongSpecifications.withGenre(settings.getGenre()),
                SongSpecifications.withEffectiveYearBetween(settings.getYearFrom(), settings.getYearTo()),
                SongSpecifications.excludingIds(usedSongIds));

        List<Song> candidates = songRepository.findAll(spec);
        if (candidates.isEmpty()) {
            throw new InsufficientCatalogException(
                    "No unused songs match the selected filters. Try a wider genre or date range.");
        }

        List<Song> pool = preferUnusedArtists(candidates, player);
        pool = applyDifficultySpacing(pool, player, settings.getDifficulty());

        return randomProvider.pick(pool);
    }

    /** Soft constraint: exclude recently used artists, but fall back to the full pool if that empties it. */
    private List<Song> preferUnusedArtists(List<Song> candidates, GamePlayer player) {
        List<String> recentArtists = gameRoundRepository.findArtistHistoryByGamePlayerId(player.getId()).stream()
                .limit(gameProperties.getRecentArtistAvoidanceWindow())
                .toList();
        if (recentArtists.isEmpty()) {
            return candidates;
        }
        List<Song> filtered = candidates.stream()
                .filter(song -> !recentArtists.contains(song.getArtist()))
                .toList();
        return filtered.isEmpty() ? candidates : filtered;
    }

    /**
     * Re-ranks candidates by how close their year is to the player's existing timeline and keeps
     * only the most extreme {@link #DIFFICULTY_BIAS_FRACTION} of the pool: widest gaps for EASY
     * (unambiguous placement), narrowest gaps for HARD (close calls). NORMAL leaves the pool as-is.
     * We bias selection this way instead of a fabricated "popularity" score, which provider
     * metadata cannot reliably support (see README "Difficulty" section).
     */
    private List<Song> applyDifficultySpacing(List<Song> pool, GamePlayer player, Difficulty difficulty) {
        List<Integer> timelineYears = player.getTimeline().stream()
                .map(entry -> entry.getSong().getEffectiveYear())
                .toList();
        if (timelineYears.isEmpty() || difficulty == Difficulty.NORMAL) {
            return pool;
        }

        Comparator<Song> byMinGapAscending =
                Comparator.comparingInt(song -> minGapToTimeline(song.getEffectiveYear(), timelineYears));
        List<Song> sorted = new ArrayList<>(pool);
        sorted.sort(difficulty == Difficulty.HARD ? byMinGapAscending : byMinGapAscending.reversed());

        int biasedSize = Math.max(1, (int) Math.ceil(sorted.size() * DIFFICULTY_BIAS_FRACTION));
        return sorted.subList(0, Math.min(biasedSize, sorted.size()));
    }

    private int minGapToTimeline(int year, List<Integer> timelineYears) {
        return timelineYears.stream().mapToInt(y -> Math.abs(y - year)).min().orElse(Integer.MAX_VALUE);
    }
}
