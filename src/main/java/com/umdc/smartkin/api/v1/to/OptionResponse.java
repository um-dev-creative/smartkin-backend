package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

/// An answer option of a quiz question. It deliberately has no `correct` flag: the server grades the answers.
///
/// @param id   the option's id
/// @param text the text shown to the child
@Schema(name = "Option")
public record OptionResponse(String id, String text) {
}
