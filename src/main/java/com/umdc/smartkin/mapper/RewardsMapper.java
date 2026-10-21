package com.umdc.smartkin.mapper;

import com.umdc.smartkin.api.v1.to.ChildAchievementResponse;
import com.umdc.smartkin.api.v1.to.PointTransactionResponse;
import com.umdc.smartkin.jpa.domain.ChildAchievementEntity;
import com.umdc.smartkin.jpa.domain.PointEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/// Maps points and earned achievements to the API representation.
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RewardsMapper {

    /// Converts a point transaction.
    ///
    /// @param point the entity
    /// @return the API representation
    @Mapping(target = "pointId", source = "id")
    PointTransactionResponse toResponse(PointEntity point);

    /// Converts an earned achievement together with its achievement.
    ///
    /// @param earned the entity
    /// @return the API representation
    @Mapping(target = "childAchievementId", source = "id")
    @Mapping(target = "achievementId", source = "achievement.id")
    @Mapping(target = "name", source = "achievement.name")
    @Mapping(target = "description", source = "achievement.description")
    @Mapping(target = "icon", source = "achievement.icon")
    @Mapping(target = "pointsValue", source = "achievement.pointsValue")
    ChildAchievementResponse toResponse(ChildAchievementEntity earned);
}
