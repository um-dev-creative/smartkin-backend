package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import com.umdc.smartkin.jpa.repository.ActivityProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressRecorderTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UUID ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private ActivityProgressRepository repository;
    private ProgressRecorder recorder;
    private AssignedActivityEntity assignment;

    @BeforeEach
    void setUp() {
        recorder = new ProgressRecorder(repository);
        assignment = new AssignedActivityEntity();
        assignment.setId(ID);
    }

    private ActivityProgressEntity saved() {
        var captor = ArgumentCaptor.forClass(ActivityProgressEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void aFailedFirstAttemptCreatesTheRowWithTheScoreButNoCompletion() {
        when(repository.findByAssignmentId(ID)).thenReturn(Optional.empty());

        recorder.record(assignment, 40, false, NOW);

        var progress = saved();
        assertEquals(40, progress.getScore());
        assertEquals(0, progress.getCompletionPercentage());
        assertEquals(NOW, progress.getStartTime());
        assertEquals(NOW, progress.getLastAccessed());
        assertNull(progress.getCompletionTime());
        assertEquals(assignment, progress.getAssignment());
    }

    @Test
    void passingCompletesTheAssignmentOnceAndKeepsTheBestScore() {
        var existing = new ActivityProgressEntity();
        existing.setAssignment(assignment);
        existing.setCompletionPercentage(30);
        existing.setStartTime(NOW.minusSeconds(900));
        existing.setScore(95);
        when(repository.findByAssignmentId(ID)).thenReturn(Optional.of(existing));

        recorder.record(assignment, 80, true, NOW);

        var progress = saved();
        assertEquals(95, progress.getScore());
        assertEquals(100, progress.getCompletionPercentage());
        assertEquals(NOW, progress.getCompletionTime());
        assertEquals(NOW.minusSeconds(900), progress.getStartTime());
    }

    @Test
    void aLaterPassNeverMovesTheCompletionTimeAndAFailNeverLowersTheCompletion() {
        var existing = new ActivityProgressEntity();
        existing.setAssignment(assignment);
        existing.setCompletionPercentage(100);
        existing.setCompletionTime(NOW.minusSeconds(60));
        existing.setScore(70);
        when(repository.findByAssignmentId(ID)).thenReturn(Optional.of(existing));

        recorder.record(assignment, 90, true, NOW);
        assertEquals(NOW.minusSeconds(60), existing.getCompletionTime());
        assertEquals(90, existing.getScore());

        recorder.record(assignment, 10, false, NOW);
        assertEquals(100, existing.getCompletionPercentage());
        assertEquals(90, existing.getScore());
    }
}
