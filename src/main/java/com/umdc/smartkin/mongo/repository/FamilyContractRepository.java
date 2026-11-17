package com.umdc.smartkin.mongo.repository;

import com.umdc.smartkin.mongo.document.FamilyContractDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/// Family contracts, keyed by the parent's user id.
public interface FamilyContractRepository extends MongoRepository<FamilyContractDocument, UUID> {
}
