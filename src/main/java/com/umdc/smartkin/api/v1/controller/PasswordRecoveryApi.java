package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.PasswordRecoveryService;
import com.umdc.smartkin.api.v1.to.ForgotPasswordRequest;
import com.umdc.smartkin.api.v1.to.ResetPasswordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "password-recovery", description = "Forgot my password: a code by e-mail, then a new password")
public interface PasswordRecoveryApi {

    /// Get the password recovery service.
    ///
    /// @return the password recovery service
    default PasswordRecoveryService getService() {
        return new PasswordRecoveryService() {};
    }

    @Operation(summary = "Ask for a password reset code",
            description = "Sends a code to the e-mail when it belongs to an active account. Always answers 202, so it does not reveal which e-mails are registered.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Accepted (an e-mail is sent only when the account exists)"),
        @ApiResponse(responseCode = "400", description = "Malformed e-mail")
    })
    @PostMapping(value = "/forgot", consumes = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<Void> forgot(@RequestBody @Valid ForgotPasswordRequest request) {
        return getService().forgot(request);
    }

    @Operation(summary = "Set a new password with the e-mailed code",
            description = "Checks the code with mercury and sets the password in backbone. The code is single use.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "The password changed"),
        @ApiResponse(responseCode = "400", description = "Malformed request"),
        @ApiResponse(responseCode = "406", description = "The code is not valid, has expired or was spent (also for an unknown account)"),
        @ApiResponse(responseCode = "422", description = "The new password does not meet the password policy; the code is spent"),
        @ApiResponse(responseCode = "502", description = "Backbone or mercury failed")
    })
    @PostMapping(value = "/reset", consumes = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<Void> reset(@RequestBody @Valid ResetPasswordRequest request) {
        return getService().reset(request);
    }
}
