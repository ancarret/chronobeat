package com.chronobeat.dto.common;

import java.time.Instant;
import java.util.List;

/** Consistent error body returned by every failed API call. */
public record ErrorResponse(Instant timestamp, int status, String error, String message, List<String> details) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(Instant.now(), status, error, message, List.of());
    }

    public static ErrorResponse of(int status, String error, String message, List<String> details) {
        return new ErrorResponse(Instant.now(), status, error, message, details);
    }
}
