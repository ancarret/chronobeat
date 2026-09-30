package com.chronobeat.exception;

import org.springframework.http.HttpStatus;

/** An online game action without (or with the wrong) {@code X-Player-Token}. */
public class InvalidPlayerTokenException extends DomainException {
    public InvalidPlayerTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Missing or invalid X-Player-Token header");
    }
}
