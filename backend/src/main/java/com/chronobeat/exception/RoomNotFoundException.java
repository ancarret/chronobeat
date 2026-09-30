package com.chronobeat.exception;

import org.springframework.http.HttpStatus;

public class RoomNotFoundException extends DomainException {
    public RoomNotFoundException(String code) {
        super(HttpStatus.NOT_FOUND, "No open room with code " + code + ". Check the code, or the game may have already started.");
    }
}
