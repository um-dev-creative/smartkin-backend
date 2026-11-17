package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.FamilyContractRequest;
import com.umdc.smartkin.api.v1.to.FamilyContractResponse;
import com.umdc.smartkin.mongo.document.FamilyContractDocument;
import com.umdc.smartkin.mongo.repository.FamilyContractRepository;
import com.umdc.smartkin.security.access.AccessPolicy;
import com.umdc.smartkin.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static com.umdc.smartkin.constant.SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.MESSAGE_ERROR_HEADER;
import static com.umdc.smartkin.constant.SmartKinAppConstants.NOT_A_PARENT_MESSAGE;

/// Keeps the contract of each parent in MongoDB. Only a parent has one, and only their own is reachable: the key is the `uid` of the token.
@Service
public class FamilyContractServiceImpl implements FamilyContractService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FamilyContractServiceImpl.class);
    static final String NO_CONTRACT_MESSAGE = "There is no family contract yet.";
    static final String STORE_UNAVAILABLE_MESSAGE = "The family contract could not be reached, try again later.";

    private final FamilyContractRepository repository;
    private final AccessPolicy accessPolicy;
    private final Clock clock;

    /// @param repository   where the contracts are kept
    /// @param accessPolicy tells who is a parent
    /// @param clock        the clock `updatedAt` and the default agreement date are read from
    public FamilyContractServiceImpl(FamilyContractRepository repository, AccessPolicy accessPolicy, Clock clock) {
        this.repository = repository;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<FamilyContractResponse> get(String token) {
        var parentId = JwtUtil.getUidFromToken(token);
        if (parentId == null) {
            return error(HttpStatus.FORBIDDEN, CALLER_UNKNOWN_MESSAGE);
        }
        if (!accessPolicy.isParent(parentId)) {
            return error(HttpStatus.FORBIDDEN, NOT_A_PARENT_MESSAGE);
        }
        try {
            return repository.findById(parentId).map(document -> ResponseEntity.ok(toResponse(document)))
                    .orElseGet(() -> error(HttpStatus.NOT_FOUND, NO_CONTRACT_MESSAGE));
        } catch (DataAccessException e) {
            return unavailable(e);
        }
    }

    @Override
    public ResponseEntity<FamilyContractResponse> put(String token, FamilyContractRequest request) {
        var parentId = JwtUtil.getUidFromToken(token);
        if (parentId == null) {
            return error(HttpStatus.FORBIDDEN, CALLER_UNKNOWN_MESSAGE);
        }
        if (!accessPolicy.isParent(parentId)) {
            return error(HttpStatus.FORBIDDEN, NOT_A_PARENT_MESSAGE);
        }
        var now = clock.instant();
        var date = request.agreementDate() != null ? request.agreementDate() : LocalDate.ofInstant(now, ZoneOffset.UTC);
        var document = new FamilyContractDocument(parentId, request.familyName().strip(), List.copyOf(request.rules().stream().map(String::strip).toList()),
                request.signature().strip(), date, now);
        try {
            return ResponseEntity.ok(toResponse(repository.save(document)));
        } catch (DataAccessException e) {
            return unavailable(e);
        }
    }

    private static FamilyContractResponse toResponse(FamilyContractDocument document) {
        return new FamilyContractResponse(document.familyName(), document.rules(), document.signature(), document.agreementDate(), document.updatedAt());
    }

    private static <T> ResponseEntity<T> unavailable(DataAccessException e) {
        LOGGER.error("The family contract store failed: {}", e.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, STORE_UNAVAILABLE_MESSAGE);
    }

    private static <T> ResponseEntity<T> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).header(MESSAGE_ERROR_HEADER, message).build();
    }
}
