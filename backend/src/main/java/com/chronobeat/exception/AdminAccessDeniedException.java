package com.chronobeat.exception;

import org.springframework.http.HttpStatus;

public class AdminAccessDeniedException extends DomainException {
    public AdminAccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "Missing or invalid X-Admin-Key header");
    }
}
