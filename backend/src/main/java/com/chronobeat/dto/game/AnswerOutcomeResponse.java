package com.chronobeat.dto.game;

/**
 * What happened to a submitted answer. In turn-based games it always resolves on the spot; in
 * shared-song games the answer is only locked in until the rest of the table has answered, and
 * nothing about its correctness exists (or is sent) until then.
 *
 * @param resolved true when the round was resolved by this submission
 * @param waitingFor how many players still have to answer before the round resolves
 * @param result this player's result; null while the round is still waiting on others
 */
public record AnswerOutcomeResponse(boolean resolved, int waitingFor, RoundResultResponse result) {}
