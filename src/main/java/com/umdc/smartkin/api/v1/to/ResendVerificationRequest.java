package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/// Asks for a new verification code for an account that is not verified yet.
///
/// @param email the e-mail the account was registered with
public record ResendVerificationRequest(@NotBlank @Email @Size(max = 250) String email) {
}
