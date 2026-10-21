package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.PointEntity;
import com.umdc.smartkin.jpa.domain.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PointRepository extends JpaRepository<PointEntity, UUID> {

    /// Whether the points of that source were already booked; consumers use it to ignore redelivered events.
    boolean existsBySourceTypeAndSourceIdAndTransactionType(String sourceType, UUID sourceId, TransactionType transactionType);

    /// The point transactions of a child, in the order of the page request.
    Page<PointEntity> findByChildId(UUID childId, Pageable pageable);
}
