package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/// Service interface for user registration-related operations.
public interface UserRegisterService {

    /// Confirms the verification code for the specified user.
    /// @param sessionTokenBkd The Backbone session token
    /// @param confirmCodeRequest the request object containing the user ID and verification code
    /// @return a ResponseEntity containing the response object and HTTP status
    default ResponseEntity<Void> confirmCode(String sessionTokenBkd, ConfirmCodeRequest confirmCodeRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Sends a new verification code to the e-mail of the user; the new code replaces the pending one.
    /// @param sessionTokenBkd the session token of the caller, who must be the user
    /// @param userId the user who asks for a new code
    /// @return 202 when the e-mail was handed over, 403 for somebody else's account, 409 when the e-mail is already verified, 502/503 when
    ///         the identity or the mail service fails
    default ResponseEntity<Void> resendCode(String sessionTokenBkd, java.util.UUID userId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

}
