package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/// A child linked to a parent.
///
/// @param childId     the child's backbone user id
/// @param parentId    the parent's backbone user id
/// @param gradeLevel  school grade
/// @param avatar      avatar reference
/// @param totalPoints points the child has earned
@Schema(name = "Child")
public record ChildResponse(UUID childId, UUID parentId, String gradeLevel, String avatar, int totalPoints) {
}
