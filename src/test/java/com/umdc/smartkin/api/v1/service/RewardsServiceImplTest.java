package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.jpa.domain.AchievementEntity;
import com.umdc.smartkin.jpa.domain.ChildAchievementEntity;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.PointEntity;
import com.umdc.smartkin.jpa.domain.TransactionType;
import com.umdc.smartkin.jpa.repository.ChildAchievementRepository;
import com.umdc.smartkin.jpa.repository.PointRepository;
import com.umdc.smartkin.mapper.RewardsMapperImpl;
import com.umdc.smartkin.security.access.AccessPolicy;
import com.umdc.smartkin.security.access.ChildAccess;
import com.umdc.smartkin.security.access.ChildAccessDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardsServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    @Mock
    private AccessPolicy accessPolicy;
    @Mock
    private PointRepository pointRepository;
    @Mock
    private ChildAchievementRepository childAchievementRepository;
    private RewardsServiceImpl service;
    private ChildEntity child;

    @BeforeEach
    void setUp() {
        service = new RewardsServiceImpl(accessPolicy, pointRepository, childAchievementRepository, new RewardsMapperImpl());
        child = new ChildEntity();
        child.setId(CHILD_ID);
        child.setTotalPoints(120);
    }

    private void parentOwns() {
        when(accessPolicy.viewChild(PARENT_ID, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.OWNER, child));
    }

    private static PointEntity point(int amount, TransactionType type) {
        var point = new PointEntity();
        point.setId(UUID.randomUUID());
        point.setAmount(amount);
        point.setSourceType("QUIZ");
        point.setSourceId(UUID.randomUUID());
        point.setTransactionType(type);
        point.setDescription("Quiz aprobado");
        point.setTransactionDate(NOW);
        return point;
    }

    @Test
    void theParentSeesTheBalanceAndAPageOfTransactionsNewestFirst() {
        parentOwns();
        var earned = point(50, TransactionType.EARN);
        var spent = point(20, TransactionType.SPEND);
        when(pointRepository.findByChildId(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(earned, spent), PageRequest.of(1, 2), 5));

        var response = service.points(tokenFor(PARENT_ID), CHILD_ID, 1, 2);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = response.getBody();
        assertEquals(CHILD_ID, body.childId());
        assertEquals(120, body.totalPoints());
        assertEquals(1, body.transactions().page());
        assertEquals(2, body.transactions().size());
        assertEquals(5, body.transactions().totalElements());
        assertEquals(3, body.transactions().totalPages());
        assertEquals(earned.getId(), body.transactions().content().get(0).pointId());
        assertEquals(TransactionType.EARN, body.transactions().content().get(0).transactionType());
        assertEquals(TransactionType.SPEND, body.transactions().content().get(1).transactionType());
        assertEquals("QUIZ", body.transactions().content().get(0).sourceType());
        assertEquals(NOW, body.transactions().content().get(0).transactionDate());
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(pointRepository).findByChildId(any(UUID.class), pageable.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "transactionDate"), pageable.getValue().getSort());
        assertEquals(1, pageable.getValue().getPageNumber());
    }

    @Test
    void theChildSeesTheirOwnPoints() {
        when(accessPolicy.viewChild(CHILD_ID, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.SELF, child));
        when(pointRepository.findByChildId(any(UUID.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        var response = service.points(tokenFor(CHILD_ID), CHILD_ID, 0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(120, response.getBody().totalPoints());
        assertEquals(0, response.getBody().transactions().content().size());
    }

    @Test
    void pointsRejectABadPageOrSizeBeforeLookingAnythingUp() {
        for (var bad : List.of(new int[]{-1, 20}, new int[]{0, 0}, new int[]{0, 101})) {
            var response = service.points(tokenFor(PARENT_ID), CHILD_ID, bad[0], bad[1]);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(SmartKinAppConstants.INVALID_PAGE_MESSAGE, response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        }
        verify(accessPolicy, never()).viewChild(any(), any());
    }

    @Test
    void pointsAndAchievementsHideForeignChildrenAndRefuseOutsiders() {
        var other = UUID.randomUUID();
        var sibling = UUID.randomUUID();
        when(accessPolicy.viewChild(other, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.NOT_VISIBLE, null));
        when(accessPolicy.viewChild(sibling, CHILD_ID)).thenReturn(new ChildAccess(ChildAccessDecision.FORBIDDEN, null));

        assertEquals(HttpStatus.NOT_FOUND, service.points(tokenFor(other), CHILD_ID, 0, 20).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, service.points(tokenFor(sibling), CHILD_ID, 0, 20).getStatusCode());
        assertEquals(SmartKinAppConstants.ACCESS_DENIED_MESSAGE,
                service.points(tokenFor(sibling), CHILD_ID, 0, 20).getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
        assertEquals(HttpStatus.FORBIDDEN, service.points(null, CHILD_ID, 0, 20).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, service.achievements(tokenFor(other), CHILD_ID).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, service.achievements(tokenFor(sibling), CHILD_ID).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, service.achievements("garbage", CHILD_ID).getStatusCode());
        verify(pointRepository, never()).findByChildId(any(), any());
        verify(childAchievementRepository, never()).findByChildIdOrderByEarnedAtDesc(any());
    }

    @Test
    void achievementsCarryTheDataOfTheAchievement() {
        parentOwns();
        var achievement = new AchievementEntity();
        achievement.setId(UUID.randomUUID());
        achievement.setName("First lesson");
        achievement.setDescription("Complete a lesson");
        achievement.setIcon("star");
        achievement.setPointsValue(10);
        var earned = new ChildAchievementEntity();
        earned.setId(UUID.randomUUID());
        earned.setChild(child);
        earned.setAchievement(achievement);
        earned.setEarnedAt(NOW);
        when(childAchievementRepository.findByChildIdOrderByEarnedAtDesc(CHILD_ID)).thenReturn(List.of(earned));

        var response = service.achievements(tokenFor(PARENT_ID), CHILD_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = response.getBody().get(0);
        assertEquals(earned.getId(), body.childAchievementId());
        assertEquals(achievement.getId(), body.achievementId());
        assertEquals("First lesson", body.name());
        assertEquals("star", body.icon());
        assertEquals(10, body.pointsValue());
        assertEquals(NOW, body.earnedAt());
    }

    @Test
    void aChildWithoutAchievementsGetsAnEmptyList() {
        parentOwns();
        when(childAchievementRepository.findByChildIdOrderByEarnedAtDesc(CHILD_ID)).thenReturn(List.of());

        var response = service.achievements(tokenFor(PARENT_ID), CHILD_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
        assertNull(response.getHeaders().getFirst(SmartKinAppConstants.MESSAGE_ERROR_HEADER));
    }
}
