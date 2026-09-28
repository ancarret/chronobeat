package com.chronobeat.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class RoundNotFoundException extends DomainException {
    public RoundNotFoundException(UUID roundId) {
        super(HttpStatus.NOT_FOUND, "Round not found: " + roundId);
    }
}
