package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/// Body of `PUT /assignments/{id}/progress`.
///
/// @param completionPercentage 0 to 100; it cannot be lower than the value already reported
public record ProgressRequest(@NotNull @Min(0) @Max(100) Integer completionPercentage) {
}
