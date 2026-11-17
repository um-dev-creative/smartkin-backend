package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/// Asks for a password reset code.
///
/// @param email the e-mail of the account
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 250) String email) {
}
