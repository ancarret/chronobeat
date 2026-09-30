package com.chronobeat.dto.game;

import com.chronobeat.domain.GameStatus;
import java.util.List;
import java.util.UUID;

/**
 * @param records one entry per profile-linked player saying which personal records this game set;
 *     guests have no entry
 */
public record GameResultsResponse(
        UUID gameId,
        GameStatus status,
        int totalRounds,
        List<PlayerResponse> players,
        UUID winningPlayerId,
        List<RecordsBrokenResponse> records) {}
