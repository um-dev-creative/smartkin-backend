package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.FeedbackRequest;
import com.umdc.smartkin.api.v1.to.ProgressRequest;
import com.umdc.smartkin.api.v1.to.ProgressResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/// Progress of a child on an assignment.
public interface ProgressService {

    /// Returns the progress of an assignment; before the child starts it is 0% with no times.
    ///
    /// @param token        the session token of the caller
    /// @param assignmentId the assignment's id
    /// @return 200 with the progress; 403 when the token has no user; 404 when the assignment does not exist or is not the caller's
    default ResponseEntity<ProgressResponse> get(String token, UUID assignmentId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Reports progress. Only the assigned child may; the first report sets the start time, the percentage cannot go down and 100 completes it.
    ///
    /// @param token        the session token of the caller
    /// @param assignmentId the assignment's id
    /// @param request      the new percentage
    /// @return 200 with the progress; 400 when the percentage goes down; 403 for the parent or a token without user;
    ///         404 when the assignment does not exist or is not visible to the caller
    default ResponseEntity<ProgressResponse> update(String token, UUID assignmentId, ProgressRequest request) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Stores the parent's feedback. Only the parent who assigned the activity may.
    ///
    /// @param token        the session token of the caller
    /// @param assignmentId the assignment's id
    /// @param request      the feedback text
    /// @return 200 with the progress; 403 for the child or a token without user; 404 when the assignment does not exist or is not visible
    default ResponseEntity<ProgressResponse> feedback(String token, UUID assignmentId, FeedbackRequest request) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
