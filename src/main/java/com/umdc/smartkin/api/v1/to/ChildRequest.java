package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/// Body of `POST /children`: links an existing backbone user as a child of the calling parent.
///
/// @param childUserId the backbone user id of the child
/// @param gradeLevel  school grade, up to 50 characters
/// @param avatar      avatar reference, up to 255 characters
public record ChildRequest(
        @NotNull UUID childUserId,
        @Size(max = 50) String gradeLevel,
        @Size(max = 255) String avatar) {
}
