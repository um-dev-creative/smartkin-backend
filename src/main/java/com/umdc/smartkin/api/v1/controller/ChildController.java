package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ChildService;
import com.umdc.smartkin.api.v1.to.ChildRequest;
import com.umdc.smartkin.api.v1.to.ChildResponse;
import com.umdc.smartkin.api.v1.to.ChildUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/// REST controller for the children of a parent.
@RestController
@RequestMapping("/api/v1/children")
public class ChildController implements ChildApi {

    private final ChildService childService;

    /// Creates the controller.
    ///
    /// @param childService the service that backs the operations
    public ChildController(ChildService childService) {
        this.childService = childService;
    }

    @Override
    public ChildService getService() {
        return childService;
    }

    @Override
    public ResponseEntity<List<ChildResponse>> listMyChildren(String token) {
        return childService.list(token);
    }

    @Override
    public ResponseEntity<ChildResponse> linkChild(String token, ChildRequest request) {
        return childService.link(token, request);
    }

    @Override
    public ResponseEntity<ChildResponse> getChild(String token, UUID childId) {
        return childService.get(token, childId);
    }

    @Override
    public ResponseEntity<ChildResponse> updateChild(String token, UUID childId, ChildUpdateRequest request) {
        return childService.update(token, childId, request);
    }
}
