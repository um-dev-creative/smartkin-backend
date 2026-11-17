package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.FamilyContractService;
import com.umdc.smartkin.api.v1.to.FamilyContractRequest;
import com.umdc.smartkin.api.v1.to.FamilyContractResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// REST controller for the family contract of the signed-in parent.
@RestController
@RequestMapping("/api/v1/parents/me/family-contract")
public class FamilyContractController implements FamilyContractApi {

    private final FamilyContractService service;

    /// @param service the service that backs the operations
    public FamilyContractController(FamilyContractService service) {
        this.service = service;
    }

    @Override
    public FamilyContractService getService() {
        return service;
    }

    @Override
    public ResponseEntity<FamilyContractResponse> getFamilyContract(String token) {
        return service.get(token);
    }

    @Override
    public ResponseEntity<FamilyContractResponse> putFamilyContract(String token, FamilyContractRequest request) {
        return service.put(token, request);
    }
}
