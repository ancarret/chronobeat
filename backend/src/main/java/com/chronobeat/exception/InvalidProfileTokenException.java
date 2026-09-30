package com.chronobeat.exception;

import org.springframework.http.HttpStatus;

public class InvalidProfileTokenException extends DomainException {
    public InvalidProfileTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Missing or invalid X-Profile-Token header");
    }
}
