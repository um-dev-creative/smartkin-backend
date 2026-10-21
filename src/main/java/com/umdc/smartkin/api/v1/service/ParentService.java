package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ParentProfileRequest;
import com.umdc.smartkin.api.v1.to.ParentProfileResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/// Operations on the profile of the authenticated parent.
public interface ParentService {

    /// Returns the profile of the caller.
    ///
    /// @param token the session token of the caller
    /// @return 200 with the profile; 403 when the token has no user or the caller is a child; 404 when there is no profile yet
    default ResponseEntity<ParentProfileResponse> getMe(String token) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Creates or replaces the profile of the caller.
    ///
    /// @param token   the session token of the caller
    /// @param request the contact data
    /// @return 200 with the saved profile; 403 when the token has no user or the caller is a child
    default ResponseEntity<ParentProfileResponse> putMe(String token, ParentProfileRequest request) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
