package io.github.randomcodespace.iq.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Uniform error envelope for the REST API: {@code {"code","message","request_id"}}
 * with the appropriate HTTP status. Stack traces and class names never reach the
 * response body — only logged at WARN with the {@code request_id} so on-call can
 * correlate.
 *
 * <p>Active in the {@code serving} profile only.
 */
@RestControllerAdvice
@Profile("serving")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String code = status != null ? status.name() : "ERROR";
        String message = ex.getReason() != null
                ? ex.getReason()
                : (status != null ? status.getReasonPhrase() : "Error");
        return ResponseEntity.status(ex.getStatusCode()).body(envelope(code, message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadInput(IllegalArgumentException ex) {
        // Validation errors are surfaceable — but never include the class name or
        // a stack trace.
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(envelope("INVALID_INPUT", ex.getMessage()));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, Object>> handleAny(Throwable ex) {
        String requestId = currentRequestId();
        log.warn("Unhandled exception (request_id={})", requestId, ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(envelope("INTERNAL_ERROR", "An internal error occurred."));
    }

    private static Map<String, Object> envelope(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("request_id", currentRequestId());
        return body;
    }

    private static String currentRequestId() {
        String id = MDC.get("request_id");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
