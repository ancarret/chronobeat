package com.chronobeat.repository;

import com.chronobeat.domain.Game;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, UUID> {

    @EntityGraph(attributePaths = "players")
    Optional<Game> findWithPlayersById(UUID id);
}
