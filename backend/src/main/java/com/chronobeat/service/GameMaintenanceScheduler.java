package com.chronobeat.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background housekeeping for online games: times out players who stopped answering, and closes rooms
 * everyone abandoned so their codes can be reused. Each game is handled in its own transaction (inside
 * {@link GameService}) and failures are contained per game, so one bad row can't stall the sweep.
 */
@Component
@ConditionalOnProperty(prefix = "chronobeat.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GameMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(GameMaintenanceScheduler.class);

    /** A room nobody started within this long is considered abandoned. */
    private static final Duration ABANDONED_LOBBY_AFTER = Duration.ofHours(2);

    /** A running online game with no activity for this long is considered abandoned. */
    private static final Duration ABANDONED_GAME_AFTER = Duration.ofHours(6);

    private final GameService gameService;

    public GameMaintenanceScheduler(GameService gameService) {
        this.gameService = gameService;
    }

    @Scheduled(fixedDelay = 1000)
    public void expireOverdueRounds() {
        for (UUID gameId : gameService.findGamesWithOverdueRounds()) {
            try {
                gameService.expireOverdueRounds(gameId);
            } catch (RuntimeException e) {
                log.warn("Could not expire overdue rounds of game {}", gameId, e);
            }
        }
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void closeAbandonedGames() {
        Instant now = Instant.now();
        for (UUID gameId : gameService.findAbandonedOnlineGames(now.minus(ABANDONED_LOBBY_AFTER), now.minus(ABANDONED_GAME_AFTER))) {
            try {
                gameService.closeAbandoned(gameId);
            } catch (RuntimeException e) {
                log.warn("Could not close abandoned game {}", gameId, e);
            }
        }
    }
}
