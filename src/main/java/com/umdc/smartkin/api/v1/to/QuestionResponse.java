package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/// A quiz question. The feedback text of the lesson is not sent: it can give the answer away.
///
/// @param id       the question's id
/// @param sceneId  the scene it refers to
/// @param type     the question type
/// @param prompt   the question
/// @param options  the answer options
/// @param points   points the question is worth
@Schema(name = "Question")
public record QuestionResponse(String id, String sceneId, String type, String prompt, List<OptionResponse> options, int points) {
}
