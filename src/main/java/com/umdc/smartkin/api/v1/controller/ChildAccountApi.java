package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ChildAccountService;
import com.umdc.smartkin.api.v1.to.ChildAccountRequest;
import com.umdc.smartkin.api.v1.to.ChildAccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;
import static com.umdc.smartkin.constant.SmartKinAppConstants.FORBIDDEN_DESCRIPTION;
import static com.umdc.smartkin.constant.SmartKinAppConstants.HTTP_FORBIDDEN;
import static com.umdc.smartkin.constant.SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION;

/// Child accounts API: a parent creates the account of their own child.
@Tag(name = "children", description = "The children API")
public interface ChildAccountApi {

    /// Provides the service that backs the operations.
    ///
    /// @return the child account service
    default ChildAccountService getService() {
        return new ChildAccountService() {};
    }

    /// Creates the account of a child and links it to the authenticated parent.
    ///
    /// @param token   the session token
    /// @param request the child's data and credentials
    /// @return the child and the user name
    @Operation(operationId = "createChildAccount", summary = "Create the account of a child and link it to the authenticated parent",
            description = "The account is created like any SmartKin account (a verification code is sent to the e-mail). The child must be under 18.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created and linked",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChildAccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or the child is not a minor", content = @Content),
            @ApiResponse(responseCode = HTTP_FORBIDDEN, description = FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = "409", description = "The e-mail is already used", content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<ChildAccountResponse> createChildAccount(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @Valid @RequestBody ChildAccountRequest request) {
        return getService().create(token, request);
    }
}
