package com.chronobeat.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The player already locked in an answer for this round and is waiting for the others. */
public class RoundAlreadyLockedException extends DomainException {
    public RoundAlreadyLockedException(UUID roundId) {
        super(HttpStatus.CONFLICT, "Round " + roundId + " already has an answer locked in");
    }
}
