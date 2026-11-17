package com.umdc.smartkin.client.backbone.to;

/// Body of backbone's e-mail lookup (`POST /api/v1/users/application/{applicationId}/lookup-by-email`); the e-mail never travels in the URL.
///
/// @param email the e-mail of the account
public record BackboneEmailLookupRequest(String email) {
}
