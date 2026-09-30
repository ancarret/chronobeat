package com.chronobeat.controller;

import com.chronobeat.domain.Profile;
import com.chronobeat.dto.room.CreateRoomRequest;
import com.chronobeat.dto.room.JoinRoomRequest;
import com.chronobeat.dto.room.RoomInfoResponse;
import com.chronobeat.dto.room.RoomJoinedResponse;
import com.chronobeat.service.ProfileService;
import com.chronobeat.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Rooms", description = "Online multiplayer: open a room, share the code, start when everyone is in")
public class RoomController {

    private final RoomService roomService;
    private final ProfileService profileService;

    public RoomController(RoomService roomService, ProfileService profileService) {
        this.roomService = roomService;
        this.profileService = profileService;
    }

    @PostMapping("/api/rooms")
    @Operation(summary = "Open an online room and become its host",
            description = "The response carries this player's secret token; it is not shown again. "
                    + "Send X-Profile-Token to have the game count towards your records.")
    public ResponseEntity<RoomJoinedResponse> createRoom(
            @RequestHeader(value = ProfileController.TOKEN_HEADER, required = false) String profileToken,
            @Valid @RequestBody CreateRoomRequest request) {
        Profile profile = profileService.findByToken(profileToken).orElse(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(request, profile));
    }

    @GetMapping("/api/rooms/{code}")
    @Operation(summary = "Preview an open room before joining it")
    public RoomInfoResponse lookup(@PathVariable String code) {
        return roomService.lookup(code);
    }

    @PostMapping("/api/rooms/{code}/join")
    @Operation(summary = "Join an open room; the response carries this player's secret token")
    public RoomJoinedResponse join(
            @PathVariable String code,
            @RequestHeader(value = ProfileController.TOKEN_HEADER, required = false) String profileToken,
            @Valid @RequestBody JoinRoomRequest request) {
        Profile profile = profileService.findByToken(profileToken).orElse(null);
        return roomService.join(code, request, profile);
    }

    @PostMapping("/api/games/{gameId}/leave")
    @Operation(summary = "Leave a room that hasn't started; if the host leaves, the next player takes over")
    public ResponseEntity<Void> leave(
            @PathVariable UUID gameId, @RequestHeader(value = GameController.PLAYER_TOKEN_HEADER, required = false) String playerToken) {
        roomService.leave(gameId, playerToken);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/games/{gameId}/players/{playerId}")
    @Operation(summary = "Host only: remove a player from a room that hasn't started")
    public ResponseEntity<Void> kick(
            @PathVariable UUID gameId,
            @PathVariable UUID playerId,
            @RequestHeader(value = GameController.PLAYER_TOKEN_HEADER, required = false) String playerToken) {
        roomService.kick(gameId, playerId, playerToken);
        return ResponseEntity.noContent().build();
    }
}
