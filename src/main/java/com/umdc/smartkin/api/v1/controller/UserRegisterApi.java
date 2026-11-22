package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.UserRegisterService;
import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
import com.umdc.smartkin.api.v1.to.ResendVerificationRequest;
import com.umdc.smartkin.api.v1.to.VerificationStatusResponse;
import com.umdc.smartkin.api.v1.to.VerifyEmailRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;

@Tag(name="user-register", description="The user register API")
public interface UserRegisterApi {

    /// Get the user register service.
    ///
    /// @return the user register service
    default UserRegisterService getService(){
        return new UserRegisterService() {};
    }

    @Operation(summary = "Confirm verification code", description = "Confirms the verification code for the specified user.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Verification code confirmed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "User not found or invalid verification code")
    })
    /// Confirms the verification code for the specified user.
    ///
    /// @param sessionToken the session token of the caller (backbone's), who must be the user
    /// @param confirmCodeRequest the request object containing the user ID and verification code
    /// @return a ResponseEntity containing the response object and HTTP status
    /// @see ConfirmCodeRequest
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<Void> confirmCode(@RequestHeader(SESSION_TOKEN_KEY) String sessionToken, @RequestBody ConfirmCodeRequest confirmCodeRequest){
        return getService().confirmCode(sessionToken, confirmCodeRequest);
    }

    /// Sends a new verification code to the user's e-mail (the old one stops working).
    ///
    /// @param sessionToken the session token of the caller (backbone's), who must be the user
    /// @param userId the user who asks for a new code
    /// @return 202 when handed to the mail service
    @Operation(summary = "Resend the verification code", description = "Sends a new verification code to the e-mail of the caller; the pending code stops working.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "The e-mail was handed to the mail service"),
        @ApiResponse(responseCode = "403", description = "Not your account"),
        @ApiResponse(responseCode = "409", description = "The e-mail is already verified"),
        @ApiResponse(responseCode = "503", description = "The mail service did not take the message")
    })
    @PostMapping("/{userId}/resend")
    default ResponseEntity<Void> resendCode(@RequestHeader(SESSION_TOKEN_KEY) String sessionToken,
                                            @org.springframework.web.bind.annotation.PathVariable java.util.UUID userId) {
        return getService().resendCode(sessionToken, userId);
    }

    @Operation(summary = "Verify the e-mail of a new account", description = "Confirms the e-mailed code of an account that has not been verified and activates it. "
            + "The visitor has no session yet: the account is named by its e-mail.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The code is right and the account is active"),
        @ApiResponse(responseCode = "400", description = "Malformed request"),
        @ApiResponse(responseCode = "406", description = "The code is not valid, was spent or has expired (`Attempts-Remaining` says how many tries are left)"),
        @ApiResponse(responseCode = "502", description = "Backbone or mercury failed")
    })
    @PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<Void> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
        return getService().verifyEmail(request);
    }

    @Operation(summary = "Ask for a new verification code by e-mail", description = "Sends a new code to the e-mail when it belongs to an account that is not verified. "
            + "Always answers 202, so it does not reveal which e-mails are registered; one code per e-mail per minute.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Accepted (an e-mail is sent only when there is a pending account)"),
        @ApiResponse(responseCode = "400", description = "Malformed e-mail")
    })
    @PostMapping(value = "/resend", consumes = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<Void> resendVerification(@RequestBody @Valid ResendVerificationRequest request) {
        return getService().resendVerification(request);
    }

    /// Whether the signed-in user confirmed the e-mail code (mercury). The session is backbone's token, which does not say it; the BFF asks
    /// this to send an account that was created active and never verified to the verification screen.
    ///
    /// @param sessionToken the session token of the caller (backbone's)
    /// @return the status of the caller
    @Operation(summary = "Verification status of the caller", description = "Says whether the signed-in user confirmed the e-mail code. "
            + "Open to an unverified session, like the rest of the verification.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The status of the caller"),
        @ApiResponse(responseCode = "401", description = "No valid session"),
        @ApiResponse(responseCode = "403", description = "The session does not identify a user"),
        @ApiResponse(responseCode = "503", description = "mercury could not say")
    })
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<VerificationStatusResponse> status(@RequestHeader(SESSION_TOKEN_KEY) String sessionToken) {
        return getService().status(sessionToken);
    }
}
