package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/// A scene of a lesson.
///
/// @param id           the scene's id
/// @param order        position in the lesson
/// @param title        the title
/// @param narration    narrated text
/// @param durationMs   duration in milliseconds
/// @param interactions the interactions of the scene
/// @param definition   animation definition
@Schema(name = "Scene")
public record SceneResponse(String id, int order, String title, String narration, long durationMs,
                            List<InteractionResponse> interactions, Map<String, Object> definition) {
}
