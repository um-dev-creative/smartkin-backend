package com.umdc.smartkin.mongo.repository;

import com.umdc.smartkin.mongo.document.InteractionEventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface InteractionEventRepository extends MongoRepository<InteractionEventDocument, UUID> {

    List<InteractionEventDocument> findByChildIdAndLessonIdOrderByOccurredAtAsc(UUID childId, UUID lessonId);

    List<InteractionEventDocument> findByAttemptId(UUID attemptId);
}
