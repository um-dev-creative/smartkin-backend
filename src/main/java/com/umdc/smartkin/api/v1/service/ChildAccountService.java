package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ChildAccountRequest;
import com.umdc.smartkin.api.v1.to.ChildAccountResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/// Creation of the account of a child by their parent.
public interface ChildAccountService {

    /// Creates the user of a child and links it to the caller in one step.
    ///
    /// @param token   the session token of the caller
    /// @param request the child's data and credentials
    /// @return 201 with the child and the user name; 400 when the child is not a minor; 403 when the caller is not a parent;
    ///         409 when the e-mail is already used; 500 when the profile could not be created (the user is removed again)
    default ResponseEntity<ChildAccountResponse> create(String token, ChildAccountRequest request) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
