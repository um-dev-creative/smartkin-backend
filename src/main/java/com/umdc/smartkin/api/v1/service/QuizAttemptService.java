package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.QuizAttemptRequest;
import com.umdc.smartkin.api.v1.to.QuizAttemptResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

/// Quiz attempts of an assignment.
public interface QuizAttemptService {

    /// Grades and stores an attempt, completes the assignment when the quiz is passed and publishes `QUIZ_COMPLETED`.
    ///
    /// @param token        the session token of the caller
    /// @param assignmentId the assignment's id
    /// @param request      the answers
    /// @return 201 with the graded attempt; 400 for answers that do not match the quiz; 403 for the parent or a token without user;
    ///         404 when the assignment is not visible or has no published quiz; 409 when `maxAttempts` is reached
    default ResponseEntity<QuizAttemptResponse> submit(String token, UUID assignmentId, QuizAttemptRequest request) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Lists the attempts of an assignment, oldest first.
    ///
    /// @param token        the session token of the caller
    /// @param assignmentId the assignment's id
    /// @return 200 with the attempts; 403 for a token without user; 404 when the assignment does not exist or is not visible
    default ResponseEntity<List<QuizAttemptResponse>> list(String token, UUID assignmentId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
