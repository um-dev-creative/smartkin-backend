package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ChildResponse;
import com.umdc.smartkin.api.v1.to.ChildUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

/// Operations on the children of the authenticated parent.
public interface ChildService {

    /// Lists the children linked to the caller.
    ///
    /// @param token the session token of the caller
    /// @return 200 with the children; 403 when the caller is not a parent
    default ResponseEntity<List<ChildResponse>> list(String token) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Returns a child to their parent or to the child themselves.
    ///
    /// @param token   the session token of the caller
    /// @param childId the child's id
    /// @return 200 with the child; 403 when the caller is neither a parent nor a child; 404 when it does not exist or is not visible
    default ResponseEntity<ChildResponse> get(String token, UUID childId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Updates the grade level and avatar of a child of the caller.
    ///
    /// @param token   the session token of the caller
    /// @param childId the child's id
    /// @param request the new data
    /// @return 200 with the child; 403 when the caller is not a parent; 404 when it does not exist or is not the caller's child
    default ResponseEntity<ChildResponse> update(String token, UUID childId, ChildUpdateRequest request) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
