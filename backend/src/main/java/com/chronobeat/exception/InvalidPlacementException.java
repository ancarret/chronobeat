package com.chronobeat.exception;

import org.springframework.http.HttpStatus;

/** The submitted insertion index is outside the valid range for the player's current timeline. */
public class InvalidPlacementException extends DomainException {
    public InvalidPlacementException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
