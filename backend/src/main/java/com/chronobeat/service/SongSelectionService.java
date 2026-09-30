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
import java.util.UUID;
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

    /** The song for one player's turn in a turn-based game, tuned to that player's own timeline. */
    public Song selectNextSong(Game game, GamePlayer player) {
        List<Song> candidates = unusedCandidates(game);

        List<Song> pool = preferUnusedArtists(candidates, gameRoundRepository.findArtistHistoryByGamePlayerId(player.getId()));
        pool = applyDifficultySpacing(pool, yearsOf(player), game.getSettings().getDifficulty());

        return randomProvider.pick(pool);
    }

    /**
     * The one song every player hears in a shared-song round. There is no single timeline to tune
     * against, so difficulty is measured against the years already on <em>anybody's</em> timeline:
     * "easy" still means clearly separated from what's on the table, "hard" means close calls.
     */
    public Song selectSharedSong(Game game) {
        List<Song> candidates = unusedCandidates(game);

        List<Song> pool = preferUnusedArtists(candidates, gameRoundRepository.findArtistHistoryByGameId(game.getId()));
        List<Integer> tableYears = game.getPlayers().stream().flatMap(p -> yearsOf(p).stream()).toList();
        pool = applyDifficultySpacing(pool, tableYears, game.getSettings().getDifficulty());

        return randomProvider.pick(pool);
    }

    private List<Song> unusedCandidates(Game game) {
        GameSettings settings = game.getSettings();
        List<UUID> usedSongIds = gameRoundRepository.findUsedSongIdsByGameId(game.getId());

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
        return candidates;
    }

    /**
     * Soft constraint: exclude recently used artists, but fall back to the full pool if that empties it.
     * {@code artistHistory} is newest first and may repeat an artist (shared rounds record one row per player).
     */
    private List<Song> preferUnusedArtists(List<Song> candidates, List<String> artistHistory) {
        List<String> recentArtists = artistHistory.stream()
                .distinct()
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

    private List<Integer> yearsOf(GamePlayer player) {
        return player.getTimeline().stream().map(entry -> entry.getSong().getEffectiveYear()).toList();
    }

    /**
     * Re-ranks candidates by how close their year is to the years already on the timeline(s) and keeps
     * only the most extreme {@link #DIFFICULTY_BIAS_FRACTION} of the pool: widest gaps for EASY
     * (unambiguous placement), narrowest gaps for HARD (close calls). NORMAL leaves the pool as-is.
     * We bias selection this way instead of a fabricated "popularity" score, which provider
     * metadata cannot reliably support (see README "Difficulty" section).
     */
    private List<Song> applyDifficultySpacing(List<Song> pool, List<Integer> timelineYears, Difficulty difficulty) {
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
