package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ChildResponse;
import com.umdc.smartkin.api.v1.to.ChildUpdateRequest;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.mapper.ChildMapper;
import com.umdc.smartkin.security.access.AccessPolicy;
import com.umdc.smartkin.security.access.ChildAccess;
import com.umdc.smartkin.util.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/// Default implementation of [ChildService]. The caller is the `uid` claim of the session token; a parent reaches
/// only their own children and a child only themselves (a child that is not visible answers 404, so ids of other families stay hidden).
@Service
public class ChildServiceImpl implements ChildService {

    private final ChildRepository childRepository;
    private final ChildMapper childMapper;
    private final AccessPolicy accessPolicy;

    /// Creates the service.
    ///
    /// @param childRepository the child repository
    /// @param childMapper     the entity mapper
    /// @param accessPolicy    who may reach which child
    public ChildServiceImpl(ChildRepository childRepository, ChildMapper childMapper, AccessPolicy accessPolicy) {
        this.childRepository = childRepository;
        this.childMapper = childMapper;
        this.accessPolicy = accessPolicy;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<ChildResponse>> list(String token) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        if (!accessPolicy.isParent(callerId)) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.NOT_A_PARENT_MESSAGE);
        }
        return ResponseEntity.ok(childRepository.findAllByParentId(callerId).stream().map(childMapper::toResponse).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<ChildResponse> get(String token, UUID childId) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        return answer(accessPolicy.viewChild(callerId, childId), access -> ResponseEntity.ok(childMapper.toResponse(access.child())));
    }

    @Override
    @Transactional
    public ResponseEntity<ChildResponse> update(String token, UUID childId, ChildUpdateRequest request) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        return answer(accessPolicy.manageChild(callerId, childId), access -> {
            var child = access.child();
            child.setGradeLevel(request.gradeLevel());
            child.setAvatar(request.avatar());
            return ResponseEntity.ok(childMapper.toResponse(childRepository.save(child)));
        });
    }

    private ResponseEntity<ChildResponse> answer(ChildAccess access, Function<ChildAccess, ResponseEntity<ChildResponse>> whenAllowed) {
        return switch (access.decision()) {
            case OWNER, SELF -> whenAllowed.apply(access);
            case NOT_VISIBLE -> error(HttpStatus.NOT_FOUND, SmartKinAppConstants.CHILD_NOT_FOUND_MESSAGE);
            case FORBIDDEN -> error(HttpStatus.FORBIDDEN, SmartKinAppConstants.NOT_A_PARENT_MESSAGE);
        };
    }

    private static <T> ResponseEntity<T> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).header(SmartKinAppConstants.MESSAGE_ERROR_HEADER, message).build();
    }
}
