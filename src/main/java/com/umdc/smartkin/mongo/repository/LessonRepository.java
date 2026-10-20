package com.umdc.smartkin.mongo.repository;

import com.umdc.smartkin.mongo.document.LessonDocument;
import com.umdc.smartkin.mongo.document.LessonStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends MongoRepository<LessonDocument, UUID> {

    Optional<LessonDocument> findBySlug(String slug);

    List<LessonDocument> findByTopicIdAndStatus(UUID topicId, LessonStatus status);
}
