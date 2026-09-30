package com.chronobeat.repository;

import com.chronobeat.domain.Game;
import com.chronobeat.domain.GameStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GameRepository extends JpaRepository<Game, UUID> {

    @EntityGraph(attributePaths = "players")
    Optional<Game> findWithPlayersById(UUID id);

    /**
     * Takes a row lock on the game for the rest of the transaction. Every state change goes through this
     * so that two players locking in at the same instant are processed one after the other: otherwise each
     * could see the other as "not answered yet" and neither would resolve the round. (No join here on
     * purpose: Postgres can't lock the nullable side of an outer join.)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Game g where g.id = :id")
    Optional<Game> lockById(UUID id);

    @EntityGraph(attributePaths = "players")
    Optional<Game> findWithPlayersByRoomCodeAndStatus(String roomCode, GameStatus status);

    boolean existsByRoomCodeAndStatusNot(String roomCode, GameStatus status);

    List<Game> findByStatusInAndUpdatedAtBefore(Collection<GameStatus> statuses, Instant before);
}
