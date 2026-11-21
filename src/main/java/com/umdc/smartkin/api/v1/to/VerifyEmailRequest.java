package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/// Confirms the e-mail of a new account with the code that was e-mailed. The visitor has no session yet, so the account is named by its e-mail.
///
/// @param email            the e-mail the account was registered with
/// @param verificationCode the code from the e-mail (`1234-5678`)
public record VerifyEmailRequest(@NotBlank @Email @Size(max = 250) String email, @NotBlank @Size(max = 20) String verificationCode) {
    @Override
    public String toString() {
        return "VerifyEmailRequest{email='" + email + "', verificationCode='<hidden>'}";
    }
}
