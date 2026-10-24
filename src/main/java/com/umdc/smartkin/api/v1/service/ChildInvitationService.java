package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.AcceptInvitationRequest;
import com.umdc.smartkin.api.v1.to.ChildResponse;
import com.umdc.smartkin.api.v1.to.InvitationRequest;
import com.umdc.smartkin.api.v1.to.InvitationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

/// Invitations a parent sends so that an existing user becomes their child.
public interface ChildInvitationService {

    /// Creates an invitation and returns its one-time code (the only time it is shown).
    ///
    /// @param token   the session token of the caller
    /// @param request the data the child profile will get
    /// @return 201 with the invitation and its code; 403 when the caller is not a parent; 409 when there are too many pending invitations
    default ResponseEntity<InvitationResponse> create(String token, InvitationRequest request) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Lists the invitations of the caller, newest first, without their codes.
    ///
    /// @param token the session token of the caller
    /// @return 200 with the invitations; 403 when the caller is not a parent
    default ResponseEntity<List<InvitationResponse>> list(String token) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Revokes a pending invitation of the caller.
    ///
    /// @param token        the session token of the caller
    /// @param invitationId the invitation
    /// @return 204; 403 when the caller is not a parent; 404 when it is not the caller's; 409 when it was already accepted
    default ResponseEntity<Void> revoke(String token, UUID invitationId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Accepts an invitation: the caller becomes a child of the parent who sent it.
    ///
    /// @param token   the session token of the caller
    /// @param request the code the parent shared
    /// @return 201 with the child; 404 when the code is unknown, used, revoked or expired; 409 when the caller already has a parent or child profile
    default ResponseEntity<ChildResponse> accept(String token, AcceptInvitationRequest request) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
