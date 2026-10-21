package com.umdc.smartkin.mapper;

import com.umdc.smartkin.api.v1.to.ChildResponse;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/// Maps the child entity to its API representation.
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ChildMapper {

    /// Converts a child entity.
    ///
    /// @param child the entity
    /// @return the API representation
    @Mapping(target = "childId", source = "id")
    @Mapping(target = "parentId", source = "parent.id")
    ChildResponse toResponse(ChildEntity child);
}
