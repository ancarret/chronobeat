package com.chronobeat.exception;

import org.springframework.http.HttpStatus;

/** Thrown when an action is requested that doesn't make sense for the game's current state. */
public class InvalidGameStateException extends DomainException {
    public InvalidGameStateException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
