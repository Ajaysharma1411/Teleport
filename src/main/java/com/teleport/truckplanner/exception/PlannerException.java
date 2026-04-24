package com.teleport.truckplanner.exception;

public class PlannerException extends RuntimeException {

    private final int statusCode;

    public PlannerException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}