package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.AssignmentRequest;
import com.umdc.smartkin.api.v1.to.AssignmentResponse;
import com.umdc.smartkin.api.v1.to.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/// Assignments of activities to children.
public interface AssignmentService {

    /// Assigns an activity to a child of the caller and publishes `ACTIVITY_ASSIGNED`.
    ///
    /// @param token   the session token of the caller
    /// @param request the child, the activity and an optional due date
    /// @return 201 with the assignment; 400 for a past due date; 403 when the caller is not a parent; 404 when the child is not the
    ///         caller's or the activity does not exist or is inactive; 409 when the child already has it open
    default ResponseEntity<AssignmentResponse> assign(String token, AssignmentRequest request) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Lists the assignments the caller may see, newest first: a parent sees their children's (optionally one child), a child their own.
    ///
    /// @param token   the session token of the caller
    /// @param childId restrict to one child, or `null`
    /// @param page    0-based page
    /// @param size    page size, 1 to 100
    /// @return 200 with the page; 400 for a bad page or size; 403 or 404 as for any child access
    default ResponseEntity<PageResponse<AssignmentResponse>> list(String token, UUID childId, int page, int size) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Returns an assignment with its progress.
    ///
    /// @param token        the session token of the caller
    /// @param assignmentId the assignment's id
    /// @return 200 with the assignment; 403 when the token has no user; 404 when it does not exist or is not the caller's
    default ResponseEntity<AssignmentResponse> get(String token, UUID assignmentId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
