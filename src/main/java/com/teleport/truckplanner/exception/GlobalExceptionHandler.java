package com.teleport.truckplanner.exception;

import com.teleport.truckplanner.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central exception → HTTP response mapping for all controllers.
 *
 * Every handler returns the same ErrorResponse shape:
 *   { "timestamp": "...", "error": "...", "details": { ... } }
 *
 * The "details" map is only present for 400 responses caused by bean-validation
 * failures; it maps each invalid field path to its constraint message.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Bean Validation (@Valid on @RequestBody) ──────────────────────────────

    /**
     * Triggered when @Valid fails on the request body.
     * Collects all field errors into a details map: { "fieldName": "message" }.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (first, second) -> first  // keep first message when field appears twice
                ));
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of("Validation failed", details));
    }

    // ── Malformed JSON ────────────────────────────────────────────────────────

    /**
     * Triggered when the request body is not valid JSON or contains type mismatches
     * (e.g. a string where a number is expected, or an unparseable date string).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of("Malformed request body: " + cause));
    }

    // ── Business-rule violations ──────────────────────────────────────────────

    /**
     * Thrown by the service layer for semantic errors that bean constraints
     * cannot express (duplicate IDs, incoherent date windows, etc.).
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessValidation(ValidationException ex) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(ex.getMessage()));
    }

    // ── Payload size guard ────────────────────────────────────────────────────

    /**
     * Thrown when the request contains more orders than the bitmask DP supports.
     * Returns 413 so the client knows to split the request, not fix a field value.
     */
    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ErrorResponse> handlePayloadTooLarge(PayloadTooLargeException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of(ex.getMessage()));
    }

    // ── Legacy planner exceptions (from /api/v1/plans endpoints) ─────────────

    /**
     * Handles PlannerException which wraps an arbitrary HTTP status code.
     * Kept for backwards compatibility with the existing /api/v1/plans API.
     */
    @ExceptionHandler(PlannerException.class)
    public ResponseEntity<ErrorResponse> handlePlanner(PlannerException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ErrorResponse.of(ex.getMessage()));
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────

    /** Safety net — prevents internal stack traces leaking to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of("An unexpected error occurred: " + ex.getMessage()));
    }
}
