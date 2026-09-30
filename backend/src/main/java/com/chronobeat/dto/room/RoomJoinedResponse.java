package com.chronobeat.dto.room;

import com.chronobeat.dto.game.GameResponse;
import java.util.UUID;

/**
 * Returned to a player who just created or joined a room. {@code playerToken} is that player's only
 * credential for the game and is never shown again, so the client has to keep it.
 */
public record RoomJoinedResponse(UUID gameId, String roomCode, UUID playerId, String playerToken, GameResponse game) {}
