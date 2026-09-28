package com.chronobeat.dto.game;

import com.chronobeat.domain.GameStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record RoundResultResponse(
        UUID roundId,
        boolean correct,
        boolean anchorRound,
        Integer submittedPosition,
        Set<Integer> validPositions,
        SongRevealResponse reveal,
        PlayerResponse player,
        List<TimelineEntryResponse> timeline,
        GameStatus gameStatus) {}
