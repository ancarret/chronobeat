package com.chronobeat.exception;

import org.springframework.http.HttpStatus;

/** No songs (or not enough unused songs) match the game's configured filters. */
public class InsufficientCatalogException extends DomainException {
    public InsufficientCatalogException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
