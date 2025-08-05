package com.umdc.smartkin.jpa.repository;

import com.umdc.smartkin.jpa.domain.TopicEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TopicRepository extends JpaRepository<TopicEntity, UUID> {
}

