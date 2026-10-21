package com.umdc.smartkin.mapper;

import com.umdc.smartkin.jpa.domain.AchievementEntity;
import com.umdc.smartkin.jpa.domain.ChildAchievementEntity;
import com.umdc.smartkin.jpa.domain.PointEntity;
import com.umdc.smartkin.jpa.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RewardsMapperTest {

    private final RewardsMapper mapper = new RewardsMapperImpl();

    @Test
    void mapsAPointTransaction() {
        var point = new PointEntity();
        var id = UUID.randomUUID();
        var source = UUID.randomUUID();
        point.setId(id);
        point.setAmount(30);
        point.setSourceType("ACHIEVEMENT");
        point.setSourceId(source);
        point.setTransactionType(TransactionType.EARN);
        point.setTransactionDate(Instant.parse("2026-10-21T12:00:00Z"));

        var response = mapper.toResponse(point);

        assertEquals(id, response.pointId());
        assertEquals(30, response.amount());
        assertEquals(TransactionType.EARN, response.transactionType());
        assertEquals("ACHIEVEMENT", response.sourceType());
        assertEquals(source, response.sourceId());
        assertNull(response.description());
        assertEquals(Instant.parse("2026-10-21T12:00:00Z"), response.transactionDate());
    }

    @Test
    void mapsAnEarnedAchievementFlatteningTheAchievement() {
        var achievement = new AchievementEntity();
        achievement.setId(UUID.randomUUID());
        achievement.setName("Streak");
        achievement.setDescription("Three days in a row");
        achievement.setPointsValue(25);
        var earned = new ChildAchievementEntity();
        earned.setId(UUID.randomUUID());
        earned.setAchievement(achievement);
        earned.setEarnedAt(Instant.parse("2026-10-20T00:00:00Z"));

        var response = mapper.toResponse(earned);

        assertEquals(earned.getId(), response.childAchievementId());
        assertEquals(achievement.getId(), response.achievementId());
        assertEquals("Streak", response.name());
        assertEquals("Three days in a row", response.description());
        assertNull(response.icon());
        assertEquals(25, response.pointsValue());
        assertEquals(Instant.parse("2026-10-20T00:00:00Z"), response.earnedAt());
    }
}
