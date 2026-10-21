package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.ChildAchievementResponse;
import com.umdc.smartkin.api.v1.to.PointsSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

/// Points and achievements of a child. Readable by the child and by the parent they are linked to.
public interface RewardsService {

    /// Returns the balance and a page of the point transactions of a child, newest first.
    ///
    /// @param token   the session token of the caller
    /// @param childId the child's id
    /// @param page    0-based page
    /// @param size    page size, 1 to 100
    /// @return 200 with the summary; 400 for a bad page or size; 403 for a token without user or a caller that is neither parent nor
    ///         the child; 404 when the child does not exist or is not the caller's
    default ResponseEntity<PointsSummaryResponse> points(String token, UUID childId, int page, int size) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Lists the achievements a child has earned, newest first.
    ///
    /// @param token   the session token of the caller
    /// @param childId the child's id
    /// @return 200 with the achievements; 403 or 404 as for the points
    default ResponseEntity<List<ChildAchievementResponse>> achievements(String token, UUID childId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
