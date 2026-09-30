package com.chronobeat.exception;

import org.springframework.http.HttpStatus;

/** An authenticated online player tried to do something that isn't theirs to do. */
public class NotYourRoundException extends DomainException {
    public NotYourRoundException() {
        this("That round belongs to another player");
    }

    public NotYourRoundException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
