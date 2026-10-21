package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/// A topic of the catalog.
///
/// @param topicId     the topic's id
/// @param name        the topic's name
/// @param description what the topic is about
/// @param icon        icon reference
/// @param ageRangeMin lowest recommended age
/// @param ageRangeMax highest recommended age
@Schema(name = "Topic")
public record TopicResponse(UUID topicId, String name, String description, String icon, Integer ageRangeMin, Integer ageRangeMax) {
}
