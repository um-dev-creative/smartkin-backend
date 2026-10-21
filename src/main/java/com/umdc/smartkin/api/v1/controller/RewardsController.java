package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.RewardsService;
import com.umdc.smartkin.api.v1.to.ChildAchievementResponse;
import com.umdc.smartkin.api.v1.to.PointsSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/// REST controller for points and achievements (shares the `/api/v1/children` base with [ChildController]).
@RestController
@RequestMapping("/api/v1/children")
public class RewardsController implements RewardsApi {

    private final RewardsService rewardsService;

    /// Creates the controller.
    ///
    /// @param rewardsService the service that backs the operations
    public RewardsController(RewardsService rewardsService) {
        this.rewardsService = rewardsService;
    }

    @Override
    public RewardsService getService() {
        return rewardsService;
    }

    @Override
    public ResponseEntity<PointsSummaryResponse> getChildPoints(String token, UUID childId, int page, int size) {
        return rewardsService.points(token, childId, page, size);
    }

    @Override
    public ResponseEntity<List<ChildAchievementResponse>> listChildAchievements(String token, UUID childId) {
        return rewardsService.achievements(token, childId);
    }
}
