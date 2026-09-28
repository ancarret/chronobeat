package com.chronobeat.dto.game;

import jakarta.validation.constraints.Min;

/** {@code insertPosition} may be null only when answering an anchor round (see {@link RoundPendingResponse#anchorRound()}). */
public record AnswerRequest(@Min(0) Integer insertPosition) {}
