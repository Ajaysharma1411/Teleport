package com.teleport.truckplanner.exception;

/**
 * Thrown when the request contains more orders than the bitmask DP can handle
 * (hard limit: 22 orders → 2^22 ≈ 4 M states).
 *
 * Mapped to HTTP 413 Payload Too Large by GlobalExceptionHandler.
 */
public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
