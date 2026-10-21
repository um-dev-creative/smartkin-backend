package com.umdc.smartkin.api.v1.to;

import com.umdc.smartkin.jpa.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/// One movement of a child's points.
///
/// @param pointId         the transaction's id
/// @param amount          points moved
/// @param transactionType `EARN` or `SPEND`
/// @param sourceType      what produced it (for example a quiz or an achievement)
/// @param sourceId        id of that source
/// @param description     human readable reason
/// @param transactionDate when it happened
@Schema(name = "PointTransaction")
public record PointTransactionResponse(UUID pointId, int amount, TransactionType transactionType, String sourceType, UUID sourceId,
                                       String description, Instant transactionDate) {
}
