package com.onextel.CallServiceApplication.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    // Handle NoAvailableFreeSwitchNodeException globally
    @ExceptionHandler(NoAvailableFreeSwitchNodeException.class)
    public ResponseEntity<ErrorResponse>
        handleNoAvailableFreeSwitchNodeException(NoAvailableFreeSwitchNodeException e) {
        ErrorResponse errorResponse = new ErrorResponse(
                "Service Unavailable",
                "No available FreeSwitch node to originate the call");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    @ExceptionHandler(ServiceShuttingDownException.class)
    public ResponseEntity<ErrorResponse> handleServiceShutdown(ServiceShuttingDownException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("Service is currently shutting down.", ex.getMessage()));
    }

    // Handle other general exceptions globally
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        ErrorResponse errorResponse = new ErrorResponse(
                "Internal Server Error",
                "An unexpected error occurred: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        ErrorResponse errorResponse = new ErrorResponse(
                "Validation Failed", e.getMessage());
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "VALIDATION_FAILED",
                        "Validation Failed: " + e.getMessage()
                ));
    }

    @ExceptionHandler(CallNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCallNotFoundException(CallNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        "CALL_NOT_FOUND_ERROR",
                        "Call not found: " + ex.getMessage()
                ));
    }

    @ExceptionHandler(RequestTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeoutException(RequestTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ErrorResponse(
                        "TIMEOUT_ERROR",
                        "Request timed out: " + ex.getMessage()
                ));
    }
}
