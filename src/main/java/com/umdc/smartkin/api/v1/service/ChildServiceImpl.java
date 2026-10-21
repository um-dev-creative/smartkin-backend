package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ChildRequest;
import com.umdc.smartkin.api.v1.to.ChildResponse;
import com.umdc.smartkin.api.v1.to.ChildUpdateRequest;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.mapper.ChildMapper;
import com.umdc.smartkin.util.JwtUtil;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Default implementation of [ChildService]. The caller is the `uid` claim of the session token; a parent reaches
/// only their own children and a child only themselves (a child that is not visible answers 404, so ids of other families stay hidden).
@Service
public class ChildServiceImpl implements ChildService {

    private static final Logger logger = LoggerFactory.getLogger(ChildServiceImpl.class);

    private final ParentRepository parentRepository;
    private final ChildRepository childRepository;
    private final BackboneClient backboneClient;
    private final ChildMapper childMapper;

    /// Creates the service.
    ///
    /// @param parentRepository the parent repository
    /// @param childRepository  the child repository
    /// @param backboneClient   used to check that the child's user exists
    /// @param childMapper      the entity mapper
    public ChildServiceImpl(ParentRepository parentRepository, ChildRepository childRepository,
                            BackboneClient backboneClient, ChildMapper childMapper) {
        this.parentRepository = parentRepository;
        this.childRepository = childRepository;
        this.backboneClient = backboneClient;
        this.childMapper = childMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<ChildResponse>> list(String token) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        if (!parentRepository.existsById(callerId)) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.NOT_A_PARENT_MESSAGE);
        }
        return ResponseEntity.ok(childRepository.findAllByParentId(callerId).stream().map(childMapper::toResponse).toList());
    }

    @Override
    @Transactional
    public ResponseEntity<ChildResponse> link(String token, ChildRequest request) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        Optional<ParentEntity> parent = parentRepository.findById(callerId);
        if (parent.isEmpty()) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.NOT_A_PARENT_MESSAGE);
        }
        var childId = request.childUserId();
        if (callerId.equals(childId)) {
            return error(HttpStatus.BAD_REQUEST, SmartKinAppConstants.CHILD_IS_CALLER_MESSAGE);
        }
        if (childRepository.existsById(childId) || parentRepository.existsById(childId)) {
            return error(HttpStatus.CONFLICT, SmartKinAppConstants.CHILD_ALREADY_LINKED_MESSAGE);
        }
        try {
            backboneClient.findUserById(childId);
        } catch (FeignException e) {
            logger.warn("Could not check the child user {}: {}", childId, e.getMessage());
            if (e.status() == HttpStatus.NOT_FOUND.value()) {
                return error(HttpStatus.BAD_REQUEST, SmartKinAppConstants.CHILD_USER_NOT_FOUND_MESSAGE);
            }
            return ResponseEntity.status(e.status()).build();
        }
        var child = new ChildEntity();
        child.setId(childId);
        child.setParent(parent.get());
        child.setGradeLevel(request.gradeLevel());
        child.setAvatar(request.avatar());
        child.setTotalPoints(0);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(childMapper.toResponse(childRepository.saveAndFlush(child)));
        } catch (DataIntegrityViolationException e) {
            logger.warn("The child {} was linked concurrently: {}", childId, e.getMessage());
            return error(HttpStatus.CONFLICT, SmartKinAppConstants.CHILD_ALREADY_LINKED_MESSAGE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<ChildResponse> get(String token, UUID childId) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        if (callerId.equals(childId)) {
            return found(childRepository.findById(childId));
        }
        if (!parentRepository.existsById(callerId)) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.NOT_A_PARENT_MESSAGE);
        }
        return found(ownedChild(callerId, childId));
    }

    @Override
    @Transactional
    public ResponseEntity<ChildResponse> update(String token, UUID childId, ChildUpdateRequest request) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        if (!parentRepository.existsById(callerId)) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.NOT_A_PARENT_MESSAGE);
        }
        var child = ownedChild(callerId, childId);
        if (child.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.CHILD_NOT_FOUND_MESSAGE);
        }
        child.get().setGradeLevel(request.gradeLevel());
        child.get().setAvatar(request.avatar());
        return ResponseEntity.ok(childMapper.toResponse(childRepository.save(child.get())));
    }

    private Optional<ChildEntity> ownedChild(UUID parentId, UUID childId) {
        return childRepository.findById(childId).filter(child -> parentId.equals(child.getParent().getId()));
    }

    private ResponseEntity<ChildResponse> found(Optional<ChildEntity> child) {
        return child.map(entity -> ResponseEntity.ok(childMapper.toResponse(entity)))
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, SmartKinAppConstants.CHILD_NOT_FOUND_MESSAGE));
    }

    private static <T> ResponseEntity<T> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).header(SmartKinAppConstants.MESSAGE_ERROR_HEADER, message).build();
    }
}
