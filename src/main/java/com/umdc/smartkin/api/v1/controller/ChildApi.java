package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ChildService;
import com.umdc.smartkin.api.v1.to.ChildResponse;
import com.umdc.smartkin.api.v1.to.ChildUpdateRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;
import static com.umdc.smartkin.constant.SmartKinAppConstants.FORBIDDEN_DESCRIPTION;
import static com.umdc.smartkin.constant.SmartKinAppConstants.HTTP_FORBIDDEN;
import static com.umdc.smartkin.constant.SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION;

/// Children API: the children linked to the authenticated parent.
@Tag(name = "children", description = "The children API")
public interface ChildApi {

    /// Provides the service that backs the operations.
    ///
    /// @return the child service
    default ChildService getService() {
        return new ChildService() {};
    }

    /// Lists the children of the authenticated parent.
    ///
    /// @param token the session token
    /// @return the children
    @Operation(operationId = "listMyChildren", summary = "Children linked to the authenticated parent")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Children",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChildResponse.class))),
            @ApiResponse(responseCode = HTTP_FORBIDDEN, description = FORBIDDEN_DESCRIPTION, content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<List<ChildResponse>> listMyChildren(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token) {
        return getService().list(token);
    }

    /// Returns a child to their parent or to the child themselves.
    ///
    /// @param token   the session token
    /// @param childId the child's id
    /// @return the child
    @Operation(operationId = "getChild", summary = "A child (the owner parent or the child themselves)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Child",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChildResponse.class))),
            @ApiResponse(responseCode = HTTP_FORBIDDEN, description = FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = "404", description = "Not found or not visible to the caller", content = @Content)
    })
    @GetMapping(value = "/{childId}", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<ChildResponse> getChild(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @NotNull @PathVariable UUID childId) {
        return getService().get(token, childId);
    }

    /// Updates the grade level and avatar of a child of the authenticated parent.
    ///
    /// @param token   the session token
    /// @param childId the child's id
    /// @param request the new data
    /// @return the updated child
    @Operation(operationId = "updateChild", summary = "Update a child's grade level and avatar (owner parent only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Child updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChildResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = HTTP_FORBIDDEN, description = FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = "404", description = "Not found or not visible to the caller", content = @Content)
    })
    @PutMapping(value = "/{childId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<ChildResponse> updateChild(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @NotNull @PathVariable UUID childId,
            @Valid @RequestBody ChildUpdateRequest request) {
        return getService().update(token, childId, request);
    }
}
