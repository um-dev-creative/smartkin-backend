package com.umdc.smartkin.mapper;

import com.umdc.smartkin.api.v1.to.ParentProfileResponse;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/// Maps the parent entity to its API representation.
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ParentMapper {

    /// Converts a parent entity.
    ///
    /// @param parent the entity
    /// @return the API representation
    @Mapping(target = "parentId", source = "id")
    ParentProfileResponse toResponse(ParentEntity parent);
}
