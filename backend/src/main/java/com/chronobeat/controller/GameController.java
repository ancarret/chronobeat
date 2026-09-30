package com.chronobeat.controller;

import com.chronobeat.domain.Profile;
import com.chronobeat.dto.game.AnswerOutcomeResponse;
import com.chronobeat.dto.game.AnswerRequest;
import com.chronobeat.dto.game.CreateGameRequest;
import com.chronobeat.dto.game.GameResponse;
import com.chronobeat.dto.game.GameResultsResponse;
import com.chronobeat.dto.game.GameStateResponse;
import com.chronobeat.dto.game.RoundPendingResponse;
import com.chronobeat.dto.game.StartGameResponse;
import com.chronobeat.dto.game.TimelineEntryResponse;
import com.chronobeat.service.GameService;
import com.chronobeat.service.GameStateService;
import com.chronobeat.service.ProfileService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Games", description = "Create and play chronology rounds")
public class GameController {

    /** Secret issued to each player of an online room; not needed on a shared device. */
    public static final String PLAYER_TOKEN_HEADER = "X-Player-Token";

    private final GameService gameService;
    private final GameStateService gameStateService;
    private final ProfileService profileService;

    public GameController(GameService gameService, GameStateService gameStateService, ProfileService profileService) {
        this.gameService = gameService;
        this.gameStateService = gameStateService;
        this.profileService = profileService;
    }

    @PostMapping("/api/games")
    @Operation(summary = "Create a new solo or shared-device game in CREATED status",
            description = "Send X-Profile-Token to have the game count towards that profile's records. "
                    + "An unknown or missing token simply makes it a guest game. Online games are created through /api/rooms.")
    public ResponseEntity<GameResponse> createGame(
            @RequestHeader(value = ProfileController.TOKEN_HEADER, required = false) String profileToken,
            @Valid @RequestBody CreateGameRequest request) {
        Profile profile = profileService.findByToken(profileToken).orElse(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(gameService.createGame(request, profile));
    }

    @GetMapping("/api/games/{gameId}")
    public GameResponse getGame(@PathVariable UUID gameId) {
        return gameService.getGame(gameId);
    }

    @PostMapping("/api/games/{gameId}/start")
    @Operation(summary = "Transition CREATED -> ACTIVE and deal the first round (host only, for online rooms)")
    public StartGameResponse startGame(
            @PathVariable UUID gameId, @RequestHeader(value = PLAYER_TOKEN_HEADER, required = false) String playerToken) {
        return gameService.startGame(gameId, playerToken);
    }

    @GetMapping("/api/games/{gameId}/state")
    @Operation(summary = "One player's complete view of the game: which phase they are in and what to show",
            description = "Poll it, or re-fetch it whenever /events says something changed. On a shared device omit "
                    + "playerId to get whoever still has to act; online games identify the player by X-Player-Token.")
    public GameStateResponse getState(
            @PathVariable UUID gameId,
            @RequestParam(required = false) UUID playerId,
            @RequestHeader(value = PLAYER_TOKEN_HEADER, required = false) String playerToken) {
        return gameStateService.stateFor(gameId, playerId, playerToken);
    }

    @GetMapping("/api/games/{gameId}/rounds/current")
    @Operation(summary = "The pending round of a player (or the first waiting one); safe to poll/retry after a page refresh")
    public RoundPendingResponse getCurrentRound(@PathVariable UUID gameId, @RequestParam(required = false) UUID playerId) {
        return gameService.getCurrentRound(gameId, playerId);
    }

    @PostMapping("/api/games/{gameId}/rounds/{roundId}/answer")
    @Operation(summary = "Lock in a placement; the round resolves once every player of it has answered",
            description = "Turn-based rounds resolve immediately. In shared-song games the outcome is `resolved: false` "
                    + "until the last player answers, and no result is revealed before then.")
    public AnswerOutcomeResponse submitAnswer(
            @PathVariable UUID gameId,
            @PathVariable UUID roundId,
            @RequestHeader(value = PLAYER_TOKEN_HEADER, required = false) String playerToken,
            @Valid @RequestBody AnswerRequest request) {
        return gameService.submitAnswer(gameId, roundId, request, playerToken);
    }

    @PostMapping("/api/games/{gameId}/next-round")
    @Operation(summary = "Deal the next round once the current one is resolved",
            description = "Pass `after` (the round number you just watched) to make this idempotent: if someone else already "
                    + "dealt the next round, nothing happens. Returns the caller's fresh view of the game.")
    public GameStateResponse nextRound(
            @PathVariable UUID gameId,
            @RequestParam(required = false) Integer after,
            @RequestParam(required = false) UUID playerId,
            @RequestHeader(value = PLAYER_TOKEN_HEADER, required = false) String playerToken) {
        gameService.advance(gameId, after, playerToken);
        return gameStateService.stateFor(gameId, playerId, playerToken);
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
