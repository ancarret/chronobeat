package com.chronobeat.dto.game;

import java.util.List;
import java.util.UUID;

/**
 * What the client is allowed to know about an unresolved round. Deliberately
 * excludes title/artist/year/album/genre/externalId of the mystery song &mdash;
 * only the preview URL and the current player's own (already-revealed) timeline
 * are exposed. See README "Preventing answer leakage".
 */
public record RoundPendingResponse(
        UUID roundId,
        UUID gameId,
        UUID playerId,
        String playerDisplayName,
        int roundNumber,
        boolean anchorRound,
        String previewUrl,
        int previewPlaySeconds,
        List<TimelineEntryResponse> timeline,
        int allowedPositionCount,
        int livesRemaining) {}
