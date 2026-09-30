package com.chronobeat.dto.room;

import com.chronobeat.domain.GameStatus;
import com.chronobeat.dto.game.GameSettingsResponse;
import java.util.UUID;

/** What someone holding a room code may see before deciding to join. */
public record RoomInfoResponse(
        String roomCode,
        UUID gameId,
        GameStatus status,
        String hostName,
        int playerCount,
        int maxPlayers,
        GameSettingsResponse settings) {}
