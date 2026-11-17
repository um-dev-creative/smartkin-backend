package com.umdc.smartkin.api.v1.to;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/// The family contract of a parent.
///
/// @param familyName    name of the family
/// @param rules         the agreed rules
/// @param signature     the parent's typed signature
/// @param agreementDate day of the agreement
/// @param updatedAt     when it was last saved
public record FamilyContractResponse(String familyName, List<String> rules, String signature, LocalDate agreementDate, Instant updatedAt) {
}
