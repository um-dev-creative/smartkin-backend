package com.umdc.smartkin.security.revocation;

/// The revocation list could not be consulted, so the session cannot be trusted. The request is refused with a 503.
public class SessionValidationUnavailableException extends RuntimeException {

    public SessionValidationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
