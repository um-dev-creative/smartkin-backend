package com.umdc.smartkin.mapper;

import com.umdc.smartkin.api.v1.to.ActivityResponse;
import com.umdc.smartkin.api.v1.to.LessonResponse;
import com.umdc.smartkin.api.v1.to.TopicResponse;
import com.umdc.smartkin.jpa.domain.ActivityEntity;
import com.umdc.smartkin.jpa.domain.TopicEntity;
import com.umdc.smartkin.mongo.document.LessonDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/// Maps the catalog entities and documents to their API representation. The lesson mapping drops what must not reach a client:
/// the `correct` flag of the options and the feedback of the questions.
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CatalogMapper {

    /// Converts a topic.
    ///
    /// @param topic the entity
    /// @return the API representation
    @Mapping(target = "topicId", source = "id")
    TopicResponse toResponse(TopicEntity topic);

    /// Converts an activity.
    ///
    /// @param activity the entity
    /// @return the API representation
    @Mapping(target = "activityId", source = "id")
    @Mapping(target = "topicId", source = "topic.id")
    ActivityResponse toResponse(ActivityEntity activity);

    /// Converts a lesson.
    ///
    /// @param lesson the document
    /// @return the API representation
    LessonResponse toResponse(LessonDocument lesson);
}
