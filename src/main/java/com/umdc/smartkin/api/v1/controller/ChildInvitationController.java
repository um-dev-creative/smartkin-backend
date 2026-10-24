package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ChildInvitationService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// REST controller for the child invitations.
@RestController
@RequestMapping("/api/v1/children/invitations")
public class ChildInvitationController implements ChildInvitationApi {

    private final ChildInvitationService invitationService;

    /// Creates the controller.
    ///
    /// @param invitationService the service that backs the operations
    public ChildInvitationController(ChildInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @Override
    public ChildInvitationService getService() {
        return invitationService;
    }
}
