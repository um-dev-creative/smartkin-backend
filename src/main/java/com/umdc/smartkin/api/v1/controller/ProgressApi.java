package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ProgressService;
import com.umdc.smartkin.api.v1.to.FeedbackRequest;
import com.umdc.smartkin.api.v1.to.ProgressRequest;
import com.umdc.smartkin.api.v1.to.ProgressResponse;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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

import java.util.UUID;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;

/// Progress API: how far a child has got on an assignment and what the parent says about it.
@Tag(name = "assignments", description = "The assignments API")
public interface ProgressApi {

    /// Provides the service that backs the operations.
    ///
    /// @return the progress service
    default ProgressService getService() {
        return new ProgressService() {};
    }

    /// Returns the progress of an assignment.
    ///
    /// @param token        the session token
    /// @param assignmentId the assignment's id
    /// @return the progress
    @Operation(operationId = "getProgress", summary = "Progress of an assignment",
            description = "Before the child starts it is 0% with no times.")
    @ApiResponses({
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_OK, description = "Progress"),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_NOT_FOUND, description = SmartKinAppConstants.NOT_FOUND_DESCRIPTION, content = @Content)
    })
    @GetMapping(value = "/{assignmentId}/progress", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<ProgressResponse> getProgress(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @NotNull @PathVariable UUID assignmentId) {
        return getService().get(token, assignmentId);
    }

    /// Reports progress on an assignment.
    ///
    /// @param token        the session token
    /// @param assignmentId the assignment's id
    /// @param request      the new percentage
    /// @return the progress
    @Operation(operationId = "updateProgress", summary = "Report progress on an assignment (the assigned child only)",
            description = "The first call sets `startTime`. The percentage cannot go down; 100 sets `completionTime`.")
    @ApiResponses({
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_OK, description = "Progress saved"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_NOT_FOUND, description = SmartKinAppConstants.NOT_FOUND_DESCRIPTION, content = @Content)
    })
    @PutMapping(value = "/{assignmentId}/progress", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<ProgressResponse> updateProgress(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @NotNull @PathVariable UUID assignmentId,
            @Valid @RequestBody ProgressRequest request) {
        return getService().update(token, assignmentId, request);
    }

    /// Stores the parent's feedback on an assignment.
    ///
    /// @param token        the session token
    /// @param assignmentId the assignment's id
    /// @param request      the feedback text
    /// @return the progress
    @Operation(operationId = "putParentFeedback", summary = "Leave feedback on an assignment (the assigning parent only)")
    @ApiResponses({
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_OK, description = "Feedback saved"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_NOT_FOUND, description = SmartKinAppConstants.NOT_FOUND_DESCRIPTION, content = @Content)
    })
    @PutMapping(value = "/{assignmentId}/feedback", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<ProgressResponse> putParentFeedback(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @NotNull @PathVariable UUID assignmentId,
            @Valid @RequestBody FeedbackRequest request) {
        return getService().feedback(token, assignmentId, request);
    }
}
