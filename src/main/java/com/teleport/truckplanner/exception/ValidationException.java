package com.teleport.truckplanner.exception;

/**
 * Thrown for business-rule violations that are not expressible as Bean Validation
 * constraints — e.g., duplicate order IDs, or a pickup date after the delivery date.
 *
 * Mapped to HTTP 400 Bad Request by GlobalExceptionHandler.
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
