package com.chronobeat.service;

import com.chronobeat.domain.GamePlayer;
import com.chronobeat.domain.Profile;
import com.chronobeat.dto.game.RecordsBrokenResponse;
import com.chronobeat.dto.profile.ProfileStatsResponse;
import com.chronobeat.dto.profile.ProfileStatsResponse.AccuracyBucket;
import com.chronobeat.dto.profile.ProfileStatsResponse.RecentGame;
import com.chronobeat.repository.GamePlayerRepository;
import com.chronobeat.repository.GameRoundRepository;
import com.chronobeat.repository.OverallStats;
import com.chronobeat.repository.RoundOutcomeCount;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Personal records and history for a profile, plus "did this game set a record?" for the results screen. */
@Service
@Transactional(readOnly = true)
public class ProfileStatsService {

    /** "No upper bound" for the created-at filter; within timestamptz's range, unlike Instant.MAX. */
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");
    private static final int RECENT_GAMES = 10;

    private final GamePlayerRepository gamePlayerRepository;
    private final GameRoundRepository gameRoundRepository;

    public ProfileStatsService(GamePlayerRepository gamePlayerRepository, GameRoundRepository gameRoundRepository) {
        this.gamePlayerRepository = gamePlayerRepository;
        this.gameRoundRepository = gameRoundRepository;
    }

    public ProfileStatsResponse statsFor(Profile profile) {
        OverallStats overall = gamePlayerRepository.overallStats(profile.getId(), FAR_FUTURE);
        int longestTimeline = longestTimelineBefore(profile, FAR_FUTURE);

        List<RoundOutcomeCount> outcomes = gameRoundRepository.outcomeCountsForProfile(profile.getId());
        List<AccuracyBucket> byDecade = bucketize(outcomes, o -> String.valueOf(o.year() / 10 * 10));
        List<AccuracyBucket> byGenre = bucketize(outcomes, o -> o.genre().name());

        List<RecentGame> recent = gamePlayerRepository
                .recentlyPlayed(profile.getId(), PageRequest.of(0, RECENT_GAMES)).stream()
                .map(p -> new RecentGame(
                        p.getGame().getId(), p.getGame().getCreatedAt(), p.getGame().getMode(), p.getScore(),
                        p.getTimeline().size(), p.getAccuracy(), p.getBestStreak()))
                .toList();

        long answered = overall.correct() + overall.incorrect();
        return new ProfileStatsResponse(
                (int) overall.games(), overall.bestScore(), longestTimeline, overall.bestStreak(),
                (int) overall.correct(), (int) overall.incorrect(),
                answered == 0 ? 0.0 : (double) overall.correct() / answered,
                byDecade, byGenre, recent);
    }

    /**
     * Compares a finished game against the same profile's <em>earlier</em> games only, so the
     * verdict for a given game never changes after the fact when a better game is played later.
     * Returns null for guest players.
     */
    public RecordsBrokenResponse recordsBrokenBy(GamePlayer player) {
        Profile profile = player.getProfile();
        if (profile == null) {
            return null;
        }
        Instant before = player.getGame().getCreatedAt();
        OverallStats previous = gamePlayerRepository.overallStats(profile.getId(), before);
        if (previous.games() == 0) {
            return new RecordsBrokenResponse(player.getId(), true, false, false, false);
        }
        return new RecordsBrokenResponse(
                player.getId(),
                false,
                player.getScore() > previous.bestScore(),
                player.getTimeline().size() > longestTimelineBefore(profile, before),
                player.getBestStreak() > previous.bestStreak());
    }

    private int longestTimelineBefore(Profile profile, Instant before) {
        return gamePlayerRepository.timelineSizesDescending(profile.getId(), before, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(Long::intValue)
                .orElse(0);
    }

    private static List<AccuracyBucket> bucketize(List<RoundOutcomeCount> outcomes, Function<RoundOutcomeCount, String> keyOf) {
        // Sorted map so decades read oldest-first and genres keep a stable order.
        Map<String, long[]> totals = new TreeMap<>();
        for (RoundOutcomeCount o : outcomes) {
            long[] t = totals.computeIfAbsent(keyOf.apply(o), k -> new long[2]);
            if (Boolean.TRUE.equals(o.correct())) {
                t[0] += o.count();
            }
            t[1] += o.count();
        }
        return totals.entrySet().stream()
                .map(e -> new AccuracyBucket(
                        e.getKey(), (int) e.getValue()[0], (int) e.getValue()[1],
                        e.getValue()[1] == 0 ? 0.0 : (double) e.getValue()[0] / e.getValue()[1]))
                .toList();
    }
}
