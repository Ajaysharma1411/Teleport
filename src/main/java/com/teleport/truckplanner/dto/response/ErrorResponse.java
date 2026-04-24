package com.teleport.truckplanner.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

// Exclude null fields so the "details" key is absent when there are no field-level errors
@JsonInclude(JsonInclude.Include.NON_NULL)

/**
 * Uniform error body returned for all 4xx / 5xx responses.
 *
 * {
 *   "timestamp": "2025-12-05T10:15:30Z",
 *   "error":     "Validation failed",
 *   "details":   { "truck.id": "truck.id is required" }   ← present only on 400
 * }
 */
public class ErrorResponse {

    private final String              timestamp;
    private final String              error;
    private final Map<String, String> details;   // null → omitted from JSON

    private ErrorResponse(String error, Map<String, String> details) {
        this.timestamp = Instant.now().toString();
        this.error     = error;
        this.details   = details;
    }

    /** For simple errors without field-level detail. */
    public static ErrorResponse of(String error) {
        return new ErrorResponse(error, null);
    }

    /** For bean-validation failures where each field can have its own message. */
    public static ErrorResponse of(String error, Map<String, String> details) {
        return new ErrorResponse(error, details);
    }

    public String              getTimestamp() { return timestamp; }
    public String              getError()     { return error; }
    public Map<String, String> getDetails()   { return details; }
}
