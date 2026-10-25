package com.umdc.smartkin.api.v1.to;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.umdc.commons.util.DateUtil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/// Body of `POST /children/accounts`: a parent creates the account of their child.
///
/// @param firstname   the child's first name
/// @param lastname    the child's last name
/// @param dateOfBirth the child's birth date; the child must be under 18
/// @param email       an e-mail address that is not used by another SmartKin user; the verification code is sent there
/// @param password    the password the child will sign in with
/// @param gradeLevel  school grade, up to 50 characters
/// @param avatar      avatar reference, up to 255 characters
public record ChildAccountRequest(
        @NotBlank @Size(max = 100) String firstname,
        @NotBlank @Size(max = 100) String lastname,
        @NotNull @JsonFormat(pattern = DateUtil.PATTERN_DATE) LocalDate dateOfBirth,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 50) String gradeLevel,
        @Size(max = 255) String avatar) {

    @Override
    public String toString() {
        return "ChildAccountRequest{firstname='" + firstname + "', lastname='" + lastname + "', email='" + email + "'}";
    }
}
