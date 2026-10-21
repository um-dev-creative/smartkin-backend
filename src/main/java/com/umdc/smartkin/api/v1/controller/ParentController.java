package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ParentService;
import com.umdc.smartkin.api.v1.to.ParentProfileRequest;
import com.umdc.smartkin.api.v1.to.ParentProfileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// REST controller for the parent profile.
@RestController
@RequestMapping("/api/v1/parents")
public class ParentController implements ParentApi {

    private final ParentService parentService;

    /// Creates the controller.
    ///
    /// @param parentService the service that backs the operations
    public ParentController(ParentService parentService) {
        this.parentService = parentService;
    }

    @Override
    public ParentService getService() {
        return parentService;
    }

    @Override
    public ResponseEntity<ParentProfileResponse> getMyParentProfile(String token) {
        return parentService.getMe(token);
    }

    @Override
    public ResponseEntity<ParentProfileResponse> putMyParentProfile(String token, ParentProfileRequest request) {
        return parentService.putMe(token, request);
    }
}
