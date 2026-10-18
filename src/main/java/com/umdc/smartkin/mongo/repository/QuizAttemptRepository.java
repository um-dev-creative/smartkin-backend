package com.umdc.smartkin.mongo.repository;

import com.umdc.smartkin.mongo.document.QuizAttemptDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface QuizAttemptRepository extends MongoRepository<QuizAttemptDocument, UUID> {

    List<QuizAttemptDocument> findByChildIdAndLessonIdOrderByAttemptNumberDesc(UUID childId, UUID lessonId);

    long countByChildIdAndLessonId(UUID childId, UUID lessonId);

    List<QuizAttemptDocument> findByAssignmentId(UUID assignmentId);
}
