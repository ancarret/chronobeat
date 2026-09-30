package com.chronobeat.controller;

import com.chronobeat.event.GameEventHub;
import com.chronobeat.exception.GameNotFoundException;
import com.chronobeat.repository.GameRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Tag(name = "Events", description = "Live notifications that a game changed")
public class GameEventsController {

    private final GameEventHub hub;
    private final GameRepository gameRepository;

    public GameEventsController(GameEventHub hub, GameRepository gameRepository) {
        this.hub = hub;
        this.gameRepository = gameRepository;
    }

    @GetMapping("/api/games/{gameId}/events")
    @Operation(summary = "Server-Sent Events: a `changed` event whenever anything in the game changes",
            description = "Events deliberately carry no game data. On each one, re-fetch GET /state. `playerId` only "
                    + "drives the lobby's connected indicator, so it grants nothing.")
    public SseEmitter events(@PathVariable UUID gameId, @RequestParam(required = false) UUID playerId) {
        if (!gameRepository.existsById(gameId)) {
            throw new GameNotFoundException(gameId);
        }
        return hub.subscribe(gameId, playerId);
    }
}
