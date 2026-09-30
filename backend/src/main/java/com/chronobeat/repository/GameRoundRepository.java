package com.chronobeat.repository;

import com.chronobeat.domain.GameRound;
import com.chronobeat.domain.RoundStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameRoundRepository extends JpaRepository<GameRound, UUID> {

    /** At most one round is ever PENDING for a given game at a time (turns are strictly sequential). */
    Optional<GameRound> findByGameIdAndStatus(UUID gameId, RoundStatus status);

    @Query("select r from GameRound r join fetch r.song where r.id = :roundId")
    Optional<GameRound> findByIdWithSong(UUID roundId);

    Optional<GameRound> findTopByGameIdOrderByRoundNumberDesc(UUID gameId);

    @Query("select r.song.id from GameRound r where r.game.id = :gameId")
    List<UUID> findUsedSongIdsByGameId(UUID gameId);

    @Query("select r.song.artist from GameRound r where r.gamePlayer.id = :gamePlayerId order by r.roundNumber desc")
    List<String> findArtistHistoryByGamePlayerId(UUID gamePlayerId);

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
