package com.chronobeat.dto.game;

import java.util.List;
import java.util.UUID;

/**
 * Everything a client needs to render one player's view of a game, in one call. Clients re-fetch
 * this whenever they are told something changed, instead of patching local state, so a refresh,
 * a dropped connection or a missed event can never leave a screen out of step with the server.
 *
 * @param viewerPlayerId the player this view was built for
 * @param round the round to answer; only in {@code ANSWERING}
 * @param summary the resolved round on show; in {@code REVEAL}, and the final round in {@code FINISHED}
 * @param waitingOnPlayerIds in {@code WAITING}, the players whose move is still missing
 * @param answerSecondsRemaining time left on the clock for the round in progress; null when untimed
 * @param connectedPlayerIds online players with a live event stream right now
 */
public record GameStateResponse(
        GameResponse game,
        GamePhase phase,
        UUID viewerPlayerId,
        RoundPendingResponse round,
        RoundSummaryResponse summary,
        List<UUID> waitingOnPlayerIds,
        Integer answerSecondsRemaining,
        List<UUID> connectedPlayerIds) {}
