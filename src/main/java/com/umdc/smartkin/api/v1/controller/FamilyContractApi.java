package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.FamilyContractService;
import com.umdc.smartkin.api.v1.to.FamilyContractRequest;
import com.umdc.smartkin.api.v1.to.FamilyContractResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;
import static com.umdc.smartkin.constant.SmartKinAppConstants.FORBIDDEN_DESCRIPTION;
import static com.umdc.smartkin.constant.SmartKinAppConstants.HTTP_FORBIDDEN;
import static com.umdc.smartkin.constant.SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION;

/// The family contract of the signed-in parent.
@Tag(name = "parents", description = "The parent profile API")
public interface FamilyContractApi {

    /// Provides the service.
    ///
    /// @return the family contract service
    default FamilyContractService getService() {
        return new FamilyContractService() {};
    }

    /// Reads the contract.
    ///
    /// @param token the session token of the caller
    /// @return the contract
    @Operation(operationId = "getFamilyContract", summary = "Family contract of the authenticated parent")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The contract",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FamilyContractResponse.class))),
            @ApiResponse(responseCode = HTTP_FORBIDDEN, description = FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = "404", description = "There is no contract yet", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<FamilyContractResponse> getFamilyContract(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token) {
        return getService().get(token);
    }

    /// Creates or replaces the contract.
    ///
    /// @param token   the session token of the caller
    /// @param request the contract
    /// @return the saved contract
    @Operation(operationId = "putFamilyContract", summary = "Create or replace the family contract of the authenticated parent")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract saved",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FamilyContractResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = HTTP_FORBIDDEN, description = FORBIDDEN_DESCRIPTION, content = @Content)
    })
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<FamilyContractResponse> putFamilyContract(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @Valid @RequestBody FamilyContractRequest request) {
        return getService().put(token, request);
    }
}
