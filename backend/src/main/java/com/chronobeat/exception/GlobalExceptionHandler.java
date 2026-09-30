package com.chronobeat.exception;

import com.chronobeat.dto.common.ErrorResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * Centralized error mapping. Never leaks stack traces to the client; unexpected
 * exceptions are logged server-side and surfaced as a generic 500.
 *
 * <p>Errors are always JSON, whatever the request asked for: an event-stream request that fails
 * (say, an unknown game) would otherwise be answered with a body the negotiated type can't carry.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex) {
        return json(ex.getStatus(), ErrorResponse.of(ex.getStatus().value(), ex.getStatus().getReasonPhrase(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::toString)
                .toList();
        return json(HttpStatus.BAD_REQUEST, ErrorResponse.of(400, "Bad Request", "Validation failed", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return json(HttpStatus.BAD_REQUEST, ErrorResponse.of(400, "Bad Request", "Malformed request body"));
    }

    /** A concurrent duplicate submission raced past the status check; treat it the same as "already resolved". */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return json(HttpStatus.CONFLICT, ErrorResponse.of(409, "Conflict", "This action was already processed by a concurrent request"));
    }

    /**
     * The client hung up mid-response, typically a closed tab on a live event stream. There is nobody
     * left to answer and nothing worth a warning, so this deliberately produces no response.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientGone(AsyncRequestNotUsableException ex) {
        log.debug("Client disconnected: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return json(HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.of(500, "Internal Server Error", "Something went wrong. Please try again."));
    }

    private static ResponseEntity<ErrorResponse> json(HttpStatus status, ErrorResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
