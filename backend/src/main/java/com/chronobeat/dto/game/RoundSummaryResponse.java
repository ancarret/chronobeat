package com.chronobeat.dto.game;

import java.util.List;

/**
 * The reveal of a fully resolved round, identical for every viewer: the song, plus how each
 * player fared. It is only ever built after the round resolved, so it can never leak a pending answer.
 */
public record RoundSummaryResponse(int roundNumber, SongRevealResponse reveal, List<RoundResultResponse> results) {}
