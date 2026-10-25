package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ChildInvitationService;
import com.umdc.smartkin.api.v1.to.AcceptInvitationRequest;
import com.umdc.smartkin.api.v1.to.ChildResponse;
import com.umdc.smartkin.api.v1.to.InvitationRequest;
import com.umdc.smartkin.api.v1.to.InvitationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;
import static com.umdc.smartkin.constant.SmartKinAppConstants.FORBIDDEN_DESCRIPTION;
import static com.umdc.smartkin.constant.SmartKinAppConstants.HTTP_FORBIDDEN;
import static com.umdc.smartkin.constant.SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION;

/// Child invitations API: how an existing user joins a family. The parent invites, the user accepts.
@Tag(name = "children", description = "The children API")
public interface ChildInvitationApi {

    /// Provides the service that backs the operations.
    ///
    /// @return the invitation service
    default ChildInvitationService getService() {
        return new ChildInvitationService() {};
    }

    /// Creates an invitation for the authenticated parent.
    ///
    /// @param token   the session token
    /// @param request the data the child profile will get
    /// @return the invitation, with its one-time code
    @Operation(operationId = "createChildInvitation", summary = "Invite an existing user to become a child of the authenticated parent",
            description = "The response carries the one-time code; it is not stored and cannot be read again.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Invitation created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = InvitationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = HTTP_FORBIDDEN, description = FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = "409", description = "Too many pending invitations", content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<InvitationResponse> createChildInvitation(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @Valid @RequestBody InvitationRequest request) {
        return getService().create(token, request);
    }

    /// Lists the invitations of the authenticated parent.
    ///
    /// @param token the session token
    /// @return the invitations, without codes
    @Operation(operationId = "listChildInvitations", summary = "Invitations sent by the authenticated parent")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitations",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = InvitationResponse.class))),
            @ApiResponse(responseCode = HTTP_FORBIDDEN, description = FORBIDDEN_DESCRIPTION, content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<List<InvitationResponse>> listChildInvitations(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token) {
        return getService().list(token);
    }

    /// Revokes a pending invitation.
    ///
    /// @param token        the session token
    /// @param invitationId the invitation
    /// @return no content
    @Operation(operationId = "revokeChildInvitation", summary = "Revoke a pending invitation of the authenticated parent")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Invitation revoked", content = @Content),
            @ApiResponse(responseCode = HTTP_FORBIDDEN, description = FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = "404", description = "Not found or not the caller's", content = @Content),
            @ApiResponse(responseCode = "409", description = "The invitation was already accepted", content = @Content)
    })
    @DeleteMapping("/{invitationId}")
    default ResponseEntity<Void> revokeChildInvitation(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @NotNull @PathVariable UUID invitationId) {
        return getService().revoke(token, invitationId);
    }

    /// Accepts an invitation as the authenticated user.
    ///
    /// @param token   the session token
    /// @param request the code
    /// @return the child profile that was created
    @Operation(operationId = "acceptChildInvitation", summary = "Accept an invitation and become a child of the parent who sent it",
            description = "Any signed-in user without a parent or child profile may call it. An unknown, used, revoked or expired code answers 404 alike.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Child created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChildResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "404", description = "The code is not valid", content = @Content),
            @ApiResponse(responseCode = "409", description = "The caller already has a parent or child profile", content = @Content)
    })
    @PostMapping(value = "/accept", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<ChildResponse> acceptChildInvitation(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @Valid @RequestBody AcceptInvitationRequest request) {
        return getService().accept(token, request);
    }
}
