package com.chronobeat.exception;

import org.springframework.http.HttpStatus;

/** Base type for all handled domain errors; carries the HTTP status it should map to. */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;

    protected DomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
