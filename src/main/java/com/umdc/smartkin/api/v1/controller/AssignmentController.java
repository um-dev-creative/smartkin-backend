package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.AssignmentService;
import com.umdc.smartkin.api.v1.to.AssignmentRequest;
import com.umdc.smartkin.api.v1.to.AssignmentResponse;
import com.umdc.smartkin.api.v1.to.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/// REST controller for assignments.
@RestController
@RequestMapping("/api/v1/assignments")
public class AssignmentController implements AssignmentApi {

    private final AssignmentService assignmentService;

    /// Creates the controller.
    ///
    /// @param assignmentService the service that backs the operations
    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @Override
    public AssignmentService getService() {
        return assignmentService;
    }

    @Override
    public ResponseEntity<PageResponse<AssignmentResponse>> listAssignments(String token, UUID childId, int page, int size) {
        return assignmentService.list(token, childId, page, size);
    }

    @Override
    public ResponseEntity<AssignmentResponse> assignActivity(String token, AssignmentRequest request) {
        return assignmentService.assign(token, request);
    }

    @Override
    public ResponseEntity<AssignmentResponse> getAssignment(String token, UUID assignmentId) {
        return assignmentService.get(token, assignmentId);
    }
}
