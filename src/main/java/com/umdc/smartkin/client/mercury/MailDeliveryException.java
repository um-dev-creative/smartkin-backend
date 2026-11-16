package com.umdc.smartkin.client.mercury;

/// Mercury did not take a message (not reachable, refused our token, or answered something other than 202). Never carries the address or the code.
public class MailDeliveryException extends RuntimeException {

    /// Builds the exception.
    ///
    /// @param message why the hand-over failed
    /// @param cause   the failure underneath
    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    /// Builds the exception.
    ///
    /// @param message why the hand-over failed
    public MailDeliveryException(String message) {
        super(message);
    }
}
