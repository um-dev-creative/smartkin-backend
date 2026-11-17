package com.umdc.smartkin.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/// The family contract of a parent, stored in `family_contracts`. There is one per parent: its id is the parent's user id.
@Document(collection = "family_contracts")
public record FamilyContractDocument(
        @Id UUID parentId,
        String familyName,
        List<String> rules,
        String signature,
        LocalDate agreementDate,
        Instant updatedAt) {
}
