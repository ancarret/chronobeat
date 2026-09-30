package com.chronobeat.dto.profile;

import com.chronobeat.domain.GameMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A profile's personal records and history.
 *
 * @param longestTimeline the most cards this profile has ever placed correctly in a single game
 * @param byDecade accuracy per release decade; {@code key} is the decade's first year (e.g. "1980")
 * @param byGenre accuracy per genre; {@code key} is the {@code MusicGenre} name
 */
public record ProfileStatsResponse(
        int gamesPlayed,
        int bestScore,
        int longestTimeline,
        int bestStreak,
        int totalCorrect,
        int totalIncorrect,
        double accuracy,
        List<AccuracyBucket> byDecade,
        List<AccuracyBucket> byGenre,
        List<RecentGame> recentGames) {

    public record AccuracyBucket(String key, int correct, int total, double accuracy) {}

    public record RecentGame(
            UUID gameId, Instant playedAt, GameMode mode, int score, int timelineSize, double accuracy, int bestStreak) {}
}
