package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ProgressService;
import com.umdc.smartkin.api.v1.to.FeedbackRequest;
import com.umdc.smartkin.api.v1.to.ProgressRequest;
import com.umdc.smartkin.api.v1.to.ProgressResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/// REST controller for the progress of an assignment (shares the `/api/v1/assignments` base with [AssignmentController]).
@RestController
@RequestMapping("/api/v1/assignments")
public class ProgressController implements ProgressApi {

    private final ProgressService progressService;

    /// Creates the controller.
    ///
    /// @param progressService the service that backs the operations
    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @Override
    public ProgressService getService() {
        return progressService;
    }

    @Override
    public ResponseEntity<ProgressResponse> getProgress(String token, UUID assignmentId) {
        return progressService.get(token, assignmentId);
    }

    @Override
    public ResponseEntity<ProgressResponse> updateProgress(String token, UUID assignmentId, ProgressRequest request) {
        return progressService.update(token, assignmentId, request);
    }

    @Override
    public ResponseEntity<ProgressResponse> putParentFeedback(String token, UUID assignmentId, FeedbackRequest request) {
        return progressService.feedback(token, assignmentId, request);
    }
}
