package com.chronobeat.dto.game;

import com.chronobeat.domain.GameStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** {@code guessCorrect} is null when the player didn't attempt to name the song, else whether that guess (title+artist+year) was exact. */
public record RoundResultResponse(
        UUID roundId,
        boolean correct,
        boolean anchorRound,
        Integer submittedPosition,
        Set<Integer> validPositions,
        Boolean guessCorrect,
        SongRevealResponse reveal,
        PlayerResponse player,
        List<TimelineEntryResponse> timeline,
        GameStatus gameStatus) {}
