package com.chronobeat.dto.game;

import com.chronobeat.domain.GameMode;
import com.chronobeat.domain.GameStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameResponse(
        UUID id,
        GameMode mode,
        GameStatus status,
        GameSettingsResponse settings,
        int currentRoundNumber,
        List<PlayerResponse> players,
        Instant createdAt) {}
