package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/// Sets a new password with the code that was e-mailed.
///
/// @param email       the e-mail of the account
/// @param code        the code of the e-mail, `####-####`
/// @param newPassword the new password; backbone checks it against the password policy
public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 250) String email,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{4}") String code,
        @NotBlank @Size(min = 8, max = 128) String newPassword) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[email=" + email + ", code=***, newPassword=***]";
    }
}
