package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/// An interaction inside a scene.
///
/// @param id     the interaction's id
/// @param type   the interaction type
/// @param prompt what is asked of the child
/// @param config type specific settings
@Schema(name = "Interaction")
public record InteractionResponse(String id, String type, String prompt, Map<String, Object> config) {
}
