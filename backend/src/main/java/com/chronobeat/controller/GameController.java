package com.chronobeat.controller;

import com.chronobeat.dto.game.AnswerRequest;
import com.chronobeat.dto.game.CreateGameRequest;
import com.chronobeat.dto.game.GameResponse;
import com.chronobeat.dto.game.GameResultsResponse;
import com.chronobeat.dto.game.RoundPendingResponse;
import com.chronobeat.dto.game.RoundResultResponse;
import com.chronobeat.dto.game.StartGameResponse;
import com.chronobeat.dto.game.TimelineEntryResponse;
import com.chronobeat.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Games", description = "Create and play chronology rounds")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/api/games")
    @Operation(summary = "Create a new game (solo or local multiplayer) in CREATED status")
    public ResponseEntity<GameResponse> createGame(@Valid @RequestBody CreateGameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gameService.createGame(request));
    }

    @GetMapping("/api/games/{gameId}")
    public GameResponse getGame(@PathVariable UUID gameId) {
        return gameService.getGame(gameId);
    }

    @PostMapping("/api/games/{gameId}/start")
    @Operation(summary = "Transition CREATED -> ACTIVE and deal the first round")
    public StartGameResponse startGame(@PathVariable UUID gameId) {
        return gameService.startGame(gameId);
    }

    @GetMapping("/api/games/{gameId}/rounds/current")
    @Operation(summary = "Fetch the currently pending round; safe to poll/retry after a page refresh")
    public RoundPendingResponse getCurrentRound(@PathVariable UUID gameId) {
        return gameService.getCurrentRound(gameId);
    }

    @PostMapping("/api/games/{gameId}/rounds/{roundId}/answer")
    @Operation(summary = "Submit a placement, resolve the round and reveal the mystery song")
    public RoundResultResponse submitAnswer(
            @PathVariable UUID gameId, @PathVariable UUID roundId, @Valid @RequestBody AnswerRequest request) {
        return gameService.submitAnswer(gameId, roundId, request);
    }

    @PostMapping("/api/games/{gameId}/next-round")
    @Operation(summary = "Advance turn order and deal the next round")
    public RoundPendingResponse nextRound(@PathVariable UUID gameId) {
        return gameService.nextRound(gameId);
    }

    @GetMapping("/api/games/{gameId}/timeline")
    public List<TimelineEntryResponse> getTimeline(@PathVariable UUID gameId, @RequestParam(required = false) UUID playerId) {
        return gameService.getTimeline(gameId, playerId);
    }

    @GetMapping("/api/games/{gameId}/results")
    public GameResultsResponse getResults(@PathVariable UUID gameId) {
        return gameService.getResults(gameId);
    }
}
