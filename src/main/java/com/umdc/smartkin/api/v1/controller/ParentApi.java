package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ParentService;
import com.umdc.smartkin.api.v1.to.ParentProfileRequest;
import com.umdc.smartkin.api.v1.to.ParentProfileResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;
import static com.umdc.smartkin.constant.SmartKinAppConstants.FORBIDDEN_DESCRIPTION;
import static com.umdc.smartkin.constant.SmartKinAppConstants.HTTP_FORBIDDEN;
import static com.umdc.smartkin.constant.SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION;

/// Parent profile API: the profile of the authenticated parent.
@Tag(name = "parents", description = "The parent profile API")
public interface ParentApi {

    /// Provides the service that backs the operations.
    ///
    /// @return the parent service
    default ParentService getService() {
        return new ParentService() {};
    }

    /// Returns the profile of the authenticated parent.
    ///
    /// @param token the session token
    /// @return the profile
    @Operation(operationId = "getMyParentProfile", summary = "Profile of the authenticated parent")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ParentProfileResponse.class))),
            @ApiResponse(responseCode = HTTP_FORBIDDEN, description = FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = "404", description = "Not found or not visible to the caller", content = @Content)
    })
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<ParentProfileResponse> getMyParentProfile(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token) {
        return getService().getMe(token);
    }

    /// Creates or updates the profile of the authenticated parent.
    ///
    /// @param token   the session token
    /// @param request the contact data
    /// @return the saved profile
    @Operation(operationId = "putMyParentProfile", summary = "Create or update the profile of the authenticated parent")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile saved",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ParentProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = HTTP_FORBIDDEN, description = FORBIDDEN_DESCRIPTION, content = @Content)
    })
    @PutMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<ParentProfileResponse> putMyParentProfile(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @Valid @RequestBody ParentProfileRequest request) {
        return getService().putMe(token, request);
    }
}
