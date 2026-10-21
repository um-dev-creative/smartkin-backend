package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/// The point balance of a child and a page of their transactions, newest first.
///
/// @param childId      the child
/// @param totalPoints  current balance
/// @param transactions a page of the point transactions
@Schema(name = "PointsSummary")
public record PointsSummaryResponse(UUID childId, int totalPoints, PageResponse<PointTransactionResponse> transactions) {
}
