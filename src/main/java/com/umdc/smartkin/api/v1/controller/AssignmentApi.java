package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.AssignmentService;
import com.umdc.smartkin.api.v1.to.AssignmentRequest;
import com.umdc.smartkin.api.v1.to.AssignmentResponse;
import com.umdc.smartkin.api.v1.to.PageResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;

/// Assignments API: activities a parent gives to a child.
@Tag(name = "assignments", description = "The assignments API")
public interface AssignmentApi {

    /// Provides the service that backs the operations.
    ///
    /// @return the assignment service
    default AssignmentService getService() {
        return new AssignmentService() {};
    }

    /// Lists the assignments the caller may see.
    ///
    /// @param token   the session token
    /// @param childId restrict to one child (optional)
    /// @param page    0-based page
    /// @param size    page size, 1 to 100
    /// @return the page of assignments
    @Operation(operationId = "listAssignments", summary = "Assignments visible to the caller",
            description = "A parent sees the assignments of their children (optionally one child); a child sees their own.")
    @ApiResponses({
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_OK, description = "Assignments"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<PageResponse<AssignmentResponse>> listAssignments(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @RequestParam(required = false) UUID childId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getService().list(token, childId, page, size);
    }

    /// Assigns an activity to a child of the caller.
    ///
    /// @param token   the session token
    /// @param request the child, the activity and an optional due date
    /// @return the assignment
    @Operation(operationId = "assignActivity", summary = "Assign an activity to a child (parent only)",
            description = "Publishes the `ACTIVITY_ASSIGNED` event; the child's inbox notification is created by the event consumer.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Assignment created"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_NOT_FOUND, description = SmartKinAppConstants.NOT_FOUND_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = "409", description = "The request conflicts with the current state", content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<AssignmentResponse> assignActivity(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @Valid @RequestBody AssignmentRequest request) {
        return getService().assign(token, request);
    }

    /// Returns an assignment with its progress.
    ///
    /// @param token        the session token
    /// @param assignmentId the assignment's id
    /// @return the assignment
    @Operation(operationId = "getAssignment", summary = "An assignment with its progress")
    @ApiResponses({
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_OK, description = "Assignment"),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_NOT_FOUND, description = SmartKinAppConstants.NOT_FOUND_DESCRIPTION, content = @Content)
    })
    @GetMapping(value = "/{assignmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<AssignmentResponse> getAssignment(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @NotNull @PathVariable UUID assignmentId) {
        return getService().get(token, assignmentId);
    }
}
