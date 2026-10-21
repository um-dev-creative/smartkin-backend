package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ChildAchievementResponse;
import com.umdc.smartkin.api.v1.to.PageResponse;
import com.umdc.smartkin.api.v1.to.PointsSummaryResponse;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.repository.ChildAchievementRepository;
import com.umdc.smartkin.jpa.repository.PointRepository;
import com.umdc.smartkin.mapper.RewardsMapper;
import com.umdc.smartkin.security.access.AccessPolicy;
import com.umdc.smartkin.security.access.ChildAccess;
import com.umdc.smartkin.security.access.ChildAccessDecision;
import com.umdc.smartkin.util.JwtUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/// Default implementation of [RewardsService]. Access comes from [AccessPolicy]: the child themselves or the parent they are linked to;
/// a child of another family answers 404. The balance is `child.total_points`, which the event consumers keep in step with the transactions.
@Service
public class RewardsServiceImpl implements RewardsService {

    private final AccessPolicy accessPolicy;
    private final PointRepository pointRepository;
    private final ChildAchievementRepository childAchievementRepository;
    private final RewardsMapper rewardsMapper;

    /// Creates the service.
    ///
    /// @param accessPolicy               who may reach which child
    /// @param pointRepository            the point repository
    /// @param childAchievementRepository the earned achievements repository
    /// @param rewardsMapper              the mapper to the API representation
    public RewardsServiceImpl(AccessPolicy accessPolicy, PointRepository pointRepository,
                              ChildAchievementRepository childAchievementRepository, RewardsMapper rewardsMapper) {
        this.accessPolicy = accessPolicy;
        this.pointRepository = pointRepository;
        this.childAchievementRepository = childAchievementRepository;
        this.rewardsMapper = rewardsMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<PointsSummaryResponse> points(String token, UUID childId, int page, int size) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        if (page < 0 || size < 1 || size > SmartKinAppConstants.MAX_PAGE_SIZE) {
            return error(HttpStatus.BAD_REQUEST, SmartKinAppConstants.INVALID_PAGE_MESSAGE);
        }
        var access = accessPolicy.viewChild(callerId, childId);
        if (!access.allowed()) {
            return denied(access);
        }
        var transactions = pointRepository.findByChildId(childId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate")));
        return ResponseEntity.ok(new PointsSummaryResponse(childId, access.child().getTotalPoints(),
                PageResponse.of(transactions, rewardsMapper::toResponse)));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<ChildAchievementResponse>> achievements(String token, UUID childId) {
        var callerId = JwtUtil.getUidFromToken(token);
        if (callerId == null) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.CALLER_UNKNOWN_MESSAGE);
        }
        var access = accessPolicy.viewChild(callerId, childId);
        if (!access.allowed()) {
            return denied(access);
        }
        return ResponseEntity.ok(childAchievementRepository.findByChildIdOrderByEarnedAtDesc(childId).stream()
                .map(rewardsMapper::toResponse).toList());
    }

    private static <T> ResponseEntity<T> denied(ChildAccess access) {
        if (access.decision() == ChildAccessDecision.FORBIDDEN) {
            return error(HttpStatus.FORBIDDEN, SmartKinAppConstants.ACCESS_DENIED_MESSAGE);
        }
        return error(HttpStatus.NOT_FOUND, SmartKinAppConstants.CHILD_NOT_FOUND_MESSAGE);
    }

    private static <T> ResponseEntity<T> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).header(SmartKinAppConstants.MESSAGE_ERROR_HEADER, message).build();
    }
}
