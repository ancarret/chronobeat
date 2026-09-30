package com.chronobeat.repository;

import com.chronobeat.domain.GameRound;
import com.chronobeat.domain.RoundStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameRoundRepository extends JpaRepository<GameRound, UUID> {

    List<GameRound> findAllByGameIdAndStatus(UUID gameId, RoundStatus status);

    @Query("select r from GameRound r join fetch r.song where r.id = :roundId")
    Optional<GameRound> findByIdWithSong(UUID roundId);

    Optional<GameRound> findTopByGameIdOrderByRoundNumberDesc(UUID gameId);

    /** Every player's round for one round number, in seating order (a turn-based round has exactly one). */
    @Query("""
            select r from GameRound r join fetch r.gamePlayer p join fetch r.song
            where r.game.id = :gameId and r.roundNumber = :roundNumber
            order by p.playerOrder
            """)
    List<GameRound> findRound(UUID gameId, int roundNumber);

    /** Songs already dealt in this game; shared-song games hold one row per player for the same song. */
    @Query("select distinct r.song.id from GameRound r where r.game.id = :gameId")
    List<UUID> findUsedSongIdsByGameId(UUID gameId);

    @Query("select r.song.artist from GameRound r where r.gamePlayer.id = :gamePlayerId order by r.roundNumber desc")
    List<String> findArtistHistoryByGamePlayerId(UUID gamePlayerId);

    /** Artists of the game's most recent songs, newest first (duplicated across players in shared games). */
    @Query("select r.song.artist from GameRound r where r.game.id = :gameId order by r.roundNumber desc")
    List<String> findArtistHistoryByGameId(UUID gameId);

    /** Unanswered rounds of games that run on a clock; the timeout sweep decides which are overdue. */
    @Query("""
            select r from GameRound r join fetch r.game g
            where r.status = com.chronobeat.domain.RoundStatus.PENDING
              and g.settings.answerSeconds is not null
              and g.status = com.chronobeat.domain.GameStatus.ACTIVE
            """)
    List<GameRound> findPendingRoundsOfTimedGames();

    @Query("""
            select new com.chronobeat.repository.RoundOutcomeCount(
                coalesce(s.canonicalReleaseYear, s.releaseYear), s.genre, r.correct, count(r))
            from GameRound r join r.song s
            where r.gamePlayer.profile.id = :profileId
              and r.anchorRound = false
              and r.status = com.chronobeat.domain.RoundStatus.RESOLVED
            group by coalesce(s.canonicalReleaseYear, s.releaseYear), s.genre, r.correct
            """)
    List<RoundOutcomeCount> outcomeCountsForProfile(UUID profileId);
}
