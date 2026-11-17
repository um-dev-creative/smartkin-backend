package com.umdc.smartkin.client.backbone.to;

/// Body of backbone's password reset (`PUT /api/v1/users/application/{applicationId}/user/{userId}/password`).
///
/// @param password the new password in clear, over TLS; it is never logged
public record BackbonePasswordResetRequest(String password) {

    @Override
    public String toString() {
        return "BackbonePasswordResetRequest[password=***]";
    }
}
