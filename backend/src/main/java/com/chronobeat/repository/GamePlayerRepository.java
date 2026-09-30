package com.chronobeat.repository;

import com.chronobeat.domain.GamePlayer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Read models over a profile's history. A game only counts once at least one round has
 * been answered, so abandoned lobbies don't inflate "games played".
 */
public interface GamePlayerRepository extends JpaRepository<GamePlayer, UUID> {

    /** Games created strictly before {@code before} count; pass a far-future instant for "all time". */
    @Query("""
            select new com.chronobeat.repository.OverallStats(
                count(p), coalesce(max(p.score), 0), coalesce(max(p.bestStreak), 0),
                coalesce(sum(p.correctAnswers), 0), coalesce(sum(p.incorrectAnswers), 0))
            from GamePlayer p join p.game g
            where p.profile.id = :profileId
              and (p.correctAnswers + p.incorrectAnswers) > 0
              and g.createdAt < :before
            """)
    OverallStats overallStats(UUID profileId, Instant before);

    /** Timeline sizes of a profile's played games, largest first (use a page of 1 for the record). */
    @Query("""
            select count(t)
            from TimelineEntry t join t.gamePlayer p join p.game g
            where p.profile.id = :profileId
              and (p.correctAnswers + p.incorrectAnswers) > 0
              and g.createdAt < :before
            group by p.id
            order by count(t) desc
            """)
    List<Long> timelineSizesDescending(UUID profileId, Instant before, Pageable pageable);

    @Query("""
            select p from GamePlayer p join fetch p.game g
            where p.profile.id = :profileId
              and (p.correctAnswers + p.incorrectAnswers) > 0
            order by g.createdAt desc
            """)
    List<GamePlayer> recentlyPlayed(UUID profileId, Pageable pageable);
}
