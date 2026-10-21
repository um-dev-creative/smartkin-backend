package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.RewardsService;
import com.umdc.smartkin.api.v1.to.ChildAchievementResponse;
import com.umdc.smartkin.api.v1.to.PointsSummaryResponse;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;

/// Rewards API: the points and the achievements of a child.
@Tag(name = "rewards", description = "The points and achievements API")
public interface RewardsApi {

    /// Provides the service that backs the operations.
    ///
    /// @return the rewards service
    default RewardsService getService() {
        return new RewardsService() {};
    }

    /// Returns the balance and a page of the point transactions of a child.
    ///
    /// @param token   the session token
    /// @param childId the child's id
    /// @param page    0-based page
    /// @param size    page size, 1 to 100
    /// @return the summary
    @Operation(operationId = "getChildPoints", summary = "Point balance and transactions of a child")
    @ApiResponses({
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_OK, description = "Points"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_NOT_FOUND, description = SmartKinAppConstants.NOT_FOUND_DESCRIPTION, content = @Content)
    })
    @GetMapping(value = "/{childId}/points", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<PointsSummaryResponse> getChildPoints(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @NotNull @PathVariable UUID childId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getService().points(token, childId, page, size);
    }

    /// Lists the achievements a child has earned.
    ///
    /// @param token   the session token
    /// @param childId the child's id
    /// @return the achievements
    @Operation(operationId = "listChildAchievements", summary = "Achievements a child has earned")
    @ApiResponses({
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_OK, description = "Achievements"),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_NOT_FOUND, description = SmartKinAppConstants.NOT_FOUND_DESCRIPTION, content = @Content)
    })
    @GetMapping(value = "/{childId}/achievements", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<List<ChildAchievementResponse>> listChildAchievements(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @NotNull @PathVariable UUID childId) {
        return getService().achievements(token, childId);
    }
}
