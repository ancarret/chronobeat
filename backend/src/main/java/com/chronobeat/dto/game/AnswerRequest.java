package com.chronobeat.dto.game;

import jakarta.validation.constraints.Min;
import java.util.UUID;

/**
 * {@code insertPosition} may be null only when answering an anchor round (see
 * {@link RoundPendingResponse#anchorRound()}). {@code guessedSongId}/{@code guessedYear}
 * are optional: a player may additionally try to name the exact mystery song (title,
 * artist and year) for a bonus life, independent of whether the placement itself is
 * correct. Both must be supplied and both must match for the guess to count.
 */
public record AnswerRequest(@Min(0) Integer insertPosition, UUID guessedSongId, Integer guessedYear) {

    public AnswerRequest(Integer insertPosition) {
        this(insertPosition, null, null);
    }
}
