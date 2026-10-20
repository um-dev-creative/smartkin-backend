package com.umdc.smartkin.security.revocation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.umdc.smartkin.constant.SmartKinAppConstants.MESSAGE_ERROR_HEADER;

/// Turns an unreachable revocation list into a 503 so the caller can retry, instead of a 500 or an accepted request.
@RestControllerAdvice
public class SessionValidationExceptionHandler {

    public SessionValidationExceptionHandler() {
        // Default constructor
    }

    @ExceptionHandler(SessionValidationUnavailableException.class)
    public ResponseEntity<Void> unavailable(SessionValidationUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(MESSAGE_ERROR_HEADER, "Session validation is temporarily unavailable.")
                .build();
    }
}
