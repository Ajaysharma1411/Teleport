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

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of("Malformed request body: " + cause));
    }

    // ── Business-rule violations ──────────────────────────────────────────────
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessValidation(ValidationException ex) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(ex.getMessage()));
    }

    // ── Payload size guard ────────────────────────────────────────────────────
    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ErrorResponse> handlePayloadTooLarge(PayloadTooLargeException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of(ex.getMessage()));
    }

    // ── Legacy planner exceptions (from /api/v1/plans endpoints) ─────────────
    @ExceptionHandler(PlannerException.class)
    public ResponseEntity<ErrorResponse> handlePlanner(PlannerException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ErrorResponse.of(ex.getMessage()));
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of("An unexpected error occurred: " + ex.getMessage()));
    }
}
