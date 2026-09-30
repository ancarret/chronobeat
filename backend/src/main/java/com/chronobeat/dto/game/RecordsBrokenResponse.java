package com.chronobeat.dto.game;

import java.util.UUID;

/**
 * Which personal records a finished game set for one profile-linked player.
 * On a profile's very first game there is nothing to beat, so {@code firstGame}
 * is true and every other flag is false.
 */
public record RecordsBrokenResponse(
        UUID playerId, boolean firstGame, boolean bestScore, boolean longestTimeline, boolean bestStreak) {}
