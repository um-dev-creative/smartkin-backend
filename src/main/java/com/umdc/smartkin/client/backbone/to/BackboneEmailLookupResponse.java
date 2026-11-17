package com.umdc.smartkin.client.backbone.to;

import java.util.UUID;

/// Who owns an e-mail in backbone: the minimum needed to start a password recovery.
///
/// @param id          the user id
/// @param email       the e-mail of the account
/// @param displayName the name the e-mail greets
/// @param active      whether the account can sign in
public record BackboneEmailLookupResponse(UUID id, String email, String displayName, Boolean active) {
}
