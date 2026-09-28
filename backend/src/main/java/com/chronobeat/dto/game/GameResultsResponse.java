package com.chronobeat.dto.game;

import com.chronobeat.domain.GameStatus;
import java.util.List;
import java.util.UUID;

public record GameResultsResponse(
        UUID gameId, GameStatus status, int totalRounds, List<PlayerResponse> players, UUID winningPlayerId) {}
