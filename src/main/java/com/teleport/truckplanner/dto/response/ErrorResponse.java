package com.teleport.truckplanner.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
