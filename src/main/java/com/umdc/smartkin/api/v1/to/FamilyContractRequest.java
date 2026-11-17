package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/// Body of `PUT /parents/me/family-contract`: the rules a family agreed on for the use of technology.
///
/// @param familyName    name of the family, up to 100 characters
/// @param rules         the agreed rules, 1 to 30, each up to 300 characters
/// @param signature     what the parent typed as their signature, up to 100 characters
/// @param agreementDate day of the agreement; today when omitted
public record FamilyContractRequest(
        @NotBlank @Size(max = 100) String familyName,
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 300) String> rules,
        @NotBlank @Size(max = 100) String signature,
        LocalDate agreementDate) {
}
