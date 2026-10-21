package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/// A published lesson, ready to play. Quiz options never say which one is correct.
///
/// @param id          the lesson's id
/// @param slug        the lesson's slug
/// @param topicId     the topic it belongs to
/// @param title       the title
/// @param description what the lesson teaches
/// @param locale      language of the content
/// @param version     version of the content
/// @param scenes      the scenes
/// @param quiz        the quiz, `null` when the lesson has none
@Schema(name = "Lesson")
public record LessonResponse(UUID id, String slug, UUID topicId, String title, String description, String locale, int version,
                             List<SceneResponse> scenes, QuizResponse quiz) {
}
