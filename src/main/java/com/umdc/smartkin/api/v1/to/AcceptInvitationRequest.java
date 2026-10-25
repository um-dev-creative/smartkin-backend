package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/// Body of `POST /children/invitations/accept`.
///
/// @param code the code the parent shared, with or without the hyphen
public record AcceptInvitationRequest(@NotBlank @Size(max = 20) String code) {
}
