package com.umdc.smartkin.mapper;

import com.umdc.smartkin.api.v1.to.AssignmentResponse;
import com.umdc.smartkin.api.v1.to.ProgressResponse;
import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/// Maps assignments and their progress to the API representation.
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AssignmentMapper {

    /// Converts an assignment without its progress (see [AssignmentResponse#withProgress]).
    ///
    /// @param assignment the entity
    /// @return the API representation
    @Mapping(target = "assignmentId", source = "id")
    @Mapping(target = "childId", source = "child.id")
    @Mapping(target = "activityId", source = "activity.id")
    @Mapping(target = "activityTitle", source = "activity.title")
    @Mapping(target = "lessonId", source = "activity.lessonId")
    @Mapping(target = "assignedBy", source = "assignedBy.id")
    @Mapping(target = "progress", ignore = true)
    AssignmentResponse toResponse(AssignedActivityEntity assignment);

    /// Converts a progress row.
    ///
    /// @param progress the entity
    /// @return the API representation
    @Mapping(target = "assignmentId", source = "assignment.id")
    ProgressResponse toResponse(ActivityProgressEntity progress);
}
