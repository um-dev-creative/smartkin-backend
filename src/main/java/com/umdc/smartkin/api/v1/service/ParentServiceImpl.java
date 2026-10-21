package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ParentProfileRequest;
import com.umdc.smartkin.api.v1.to.ParentProfileResponse;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.mapper.ParentMapper;
import com.umdc.smartkin.security.access.AccessPolicy;
import com.umdc.smartkin.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.umdc.smartkin.constant.SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.CHILD_CANNOT_BE_PARENT_MESSAGE;
import static com.umdc.smartkin.constant.SmartKinAppConstants.MESSAGE_ERROR_HEADER;
import static com.umdc.smartkin.constant.SmartKinAppConstants.PARENT_PROFILE_NOT_FOUND_MESSAGE;

/// Default implementation of [ParentService]. The caller is the `uid` claim of the session token.
@Service
public class ParentServiceImpl implements ParentService {

    private static final Logger logger = LoggerFactory.getLogger(ParentServiceImpl.class);

    private final ParentRepository parentRepository;
    private final AccessPolicy accessPolicy;
    private final ParentMapper parentMapper;

    /// Creates the service.
    ///
    /// @param parentRepository the parent repository
    /// @param accessPolicy     tells a child from a parent
    /// @param parentMapper     the entity mapper
    public ParentServiceImpl(ParentRepository parentRepository, AccessPolicy accessPolicy, ParentMapper parentMapper) {
        this.parentRepository = parentRepository;
        this.accessPolicy = accessPolicy;
        this.parentMapper = parentMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<ParentProfileResponse> getMe(String token) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, CALLER_UNKNOWN_MESSAGE);
        }
        var parent = parentRepository.findById(callerId);
        if (parent.isPresent()) {
            return ResponseEntity.ok(parentMapper.toResponse(parent.get()));
        }
        if (accessPolicy.isChild(callerId)) {
            return error(HttpStatus.FORBIDDEN, CHILD_CANNOT_BE_PARENT_MESSAGE);
        }
        return error(HttpStatus.NOT_FOUND, PARENT_PROFILE_NOT_FOUND_MESSAGE);
    }

    @Override
    @Transactional
    public ResponseEntity<ParentProfileResponse> putMe(String token, ParentProfileRequest request) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, CALLER_UNKNOWN_MESSAGE);
        }
        if (accessPolicy.isChild(callerId)) {
            return error(HttpStatus.FORBIDDEN, CHILD_CANNOT_BE_PARENT_MESSAGE);
        }
        var parent = parentRepository.findById(callerId).orElseGet(() -> {
            var created = new ParentEntity();
            created.setId(callerId);
            return created;
        });
        parent.setPhoneNumber(request.phoneNumber());
        parent.setAddress(request.address());
        parent.setProfilePicture(request.profilePicture());
        logger.debug("Saving the parent profile of {}", callerId);
        return ResponseEntity.ok(parentMapper.toResponse(parentRepository.save(parent)));
    }

    private static ResponseEntity<ParentProfileResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).header(MESSAGE_ERROR_HEADER, message).build();
    }
}
