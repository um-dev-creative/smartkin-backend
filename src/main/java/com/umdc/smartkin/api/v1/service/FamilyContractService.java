package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.FamilyContractRequest;
import com.umdc.smartkin.api.v1.to.FamilyContractResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/// The family contract of the signed-in parent.
public interface FamilyContractService {

    /// Reads the contract of the caller.
    ///
    /// @param token the session token of the caller
    /// @return 200 with the contract, 404 when there is none, 403 when the caller is not a parent
    default ResponseEntity<FamilyContractResponse> get(String token) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Creates or replaces the contract of the caller.
    ///
    /// @param token   the session token of the caller
    /// @param request the contract
    /// @return 200 with the saved contract, 403 when the caller is not a parent
    default ResponseEntity<FamilyContractResponse> put(String token, FamilyContractRequest request) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
