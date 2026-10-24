package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ChildAccountRequest;
import com.umdc.smartkin.api.v1.to.ChildAccountResponse;
import com.umdc.smartkin.api.v1.to.UserCreateRequest;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.mapper.ChildMapper;
import com.umdc.smartkin.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/// Default implementation of [ChildAccountService]. It reuses [UserService#create] (alias generation, backbone user, verification e-mail), so a child's
/// account is created exactly like any other SmartKin account, then links it to the calling parent. If the link fails the new user is deleted,
/// so no orphan account is left behind.
@Service
public class ChildAccountServiceImpl implements ChildAccountService {

    private static final Logger logger = LoggerFactory.getLogger(ChildAccountServiceImpl.class);

    private final ParentRepository parentRepository;
    private final UserService userService;
    private final ChildLinker childLinker;
    private final ChildMapper childMapper;
    private final Clock clock;

    /// Creates the service.
    ///
    /// @param parentRepository the parent repository
    /// @param userService      creates and deletes the backbone user
    /// @param childLinker      creates the child profile
    /// @param childMapper      the entity mapper
    /// @param clock            the current time
    public ChildAccountServiceImpl(ParentRepository parentRepository, UserService userService, ChildLinker childLinker,
                                   ChildMapper childMapper, Clock clock) {
        this.parentRepository = parentRepository;
        this.userService = userService;
        this.childLinker = childLinker;
        this.childMapper = childMapper;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<ChildAccountResponse> create(String token, ChildAccountRequest request) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        var parent = parentRepository.findById(callerId);
        if (parent.isEmpty()) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.NOT_A_PARENT_MESSAGE);
        }
        var today = LocalDate.now(clock);
        if (!request.dateOfBirth().isBefore(today) || !request.dateOfBirth().isAfter(today.minusYears(SmartKinAppConstants.ADULT_AGE_YEARS))) {
            return error(HttpStatus.BAD_REQUEST, SmartKinAppConstants.CHILD_MUST_BE_MINOR_MESSAGE);
        }
        var phone = parent.get().getPhoneNumber() == null ? "" : parent.get().getPhoneNumber();
        var created = userService.create(new UserCreateRequest(request.password(), request.email(), request.firstname(), request.lastname(),
                request.dateOfBirth(), phone, request.firstname(), false, true, false));
        if (!created.getStatusCode().is2xxSuccessful() || created.getBody() == null) {
            return relay(created.getStatusCode().value(), created.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        }
        var user = created.getBody();
        try {
            var child = childLinker.create(parent.get(), user.id(), request.gradeLevel(), request.avatar());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ChildAccountResponse(childMapper.toResponse(child), user.alias(), user.email()));
        } catch (RuntimeException e) {
            logger.error("The child profile of the new user {} failed, removing the user: {}", user.id(), e.getMessage());
            userService.deleteUserByUserAndApplication(user.id());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, SmartKinAppConstants.CHILD_ACCOUNT_FAILED_MESSAGE);
        }
    }

    private static <T> ResponseEntity<T> relay(int status, String message) {
        return message == null ? ResponseEntity.status(status).build()
                : ResponseEntity.status(status).header(SmartKinAppConstants.MESSAGE_ERROR_HEADER, message).build();
    }

    private static <T> ResponseEntity<T> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).header(SmartKinAppConstants.MESSAGE_ERROR_HEADER, message).build();
    }
}
