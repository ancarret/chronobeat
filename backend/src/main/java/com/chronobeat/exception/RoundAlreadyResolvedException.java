package com.chronobeat.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Guards against duplicate answer submissions (double-click, retried request, etc). */
public class RoundAlreadyResolvedException extends DomainException {
    public RoundAlreadyResolvedException(UUID roundId) {
        super(HttpStatus.CONFLICT, "Round already resolved: " + roundId);
    }
}
