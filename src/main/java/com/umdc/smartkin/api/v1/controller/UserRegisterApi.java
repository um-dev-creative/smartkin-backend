package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.UserRegisterService;
import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

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
    /// @param confirmCodeRequest the request object containing the user ID and verification code
    /// @return a ResponseEntity containing the response object and HTTP status
    /// @see ConfirmCodeRequest
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<Void> confirmCode(@RequestHeader("session-token-bkd") String sessionTokenBkd, @RequestBody ConfirmCodeRequest confirmCodeRequest){
        return getService().confirmCode(sessionTokenBkd, confirmCodeRequest);
    }

    /// Sends a new verification code to the user's e-mail (the old one stops working).
    ///
    /// @param sessionTokenBkd the session token of the caller, who must be the user
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
    default ResponseEntity<Void> resendCode(@RequestHeader("session-token-bkd") String sessionTokenBkd,
                                            @org.springframework.web.bind.annotation.PathVariable java.util.UUID userId) {
        return getService().resendCode(sessionTokenBkd, userId);
    }
}
