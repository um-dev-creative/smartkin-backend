package com.umdc.smartkin.client.mercury.to;

import java.util.UUID;

/// A usable template of the application as mercury lists it (`GET /api/v1/messages/template-definitions`).
///
/// @param templateDefinedId the id a message is sent with
/// @param location          the template file, e.g. `sk_verification_es.ftl`
/// @param language          its language, e.g. `es`
public record MercuryTemplateDefinition(UUID templateDefinedId, String location, String language) {
}
