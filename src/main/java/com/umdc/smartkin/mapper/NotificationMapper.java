package com.umdc.smartkin.mapper;

import com.umdc.smartkin.api.v1.to.NotificationResponse;
import com.umdc.smartkin.jpa.domain.NotificationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/// Maps notifications to the API representation.
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationMapper {

    /// Converts a notification.
    ///
    /// @param notification the entity
    /// @return the API representation
    @Mapping(target = "notificationId", source = "id")
    @Mapping(target = "read", source = "isRead")
    NotificationResponse toResponse(NotificationEntity notification);
}
