package com.chronobeat.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class GameNotFoundException extends DomainException {
    public GameNotFoundException(UUID gameId) {
        super(HttpStatus.NOT_FOUND, "Game not found: " + gameId);
    }
}
