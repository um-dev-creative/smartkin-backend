package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.Size;

/// Body of `PUT /children/{childId}`: the editable data of a child.
///
/// @param gradeLevel school grade, up to 50 characters
/// @param avatar     avatar reference, up to 255 characters
public record ChildUpdateRequest(
        @Size(max = 50) String gradeLevel,
        @Size(max = 255) String avatar) {
}
