package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ForgotPasswordRequest;
import com.umdc.smartkin.api.v1.to.ResetPasswordRequest;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.http.ResponseEntity;

/// Password recovery of a visitor who cannot sign in: a code goes to the e-mail of the account and, with it, a new password is set.
public interface PasswordRecoveryService {

    /// Sends a reset code to the e-mail when it belongs to an active account. The answer never says whether it does.
    ///
    /// @param request the e-mail
    /// @return always 202
    default ResponseEntity<Void> forgot(ForgotPasswordRequest request) {
        throw new NotImplementedException();
    }

    /// Checks the code with mercury and sets the new password in backbone.
    ///
    /// @param request the e-mail, the code and the new password
    /// @return 204 changed, 406 the code is not valid (wrong, spent, expired or no such account), 422 the password breaks the policy
    default ResponseEntity<Void> reset(ResetPasswordRequest request) {
        throw new NotImplementedException();
    }
}
