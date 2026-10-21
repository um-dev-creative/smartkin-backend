package com.umdc.smartkin.mapper;

import com.umdc.smartkin.jpa.domain.ActivityEntity;
import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.ACTIVITY_ID;
import static com.umdc.smartkin.support.TestData.CHILD_ID;
import static com.umdc.smartkin.support.TestData.LESSON_ID;
import static com.umdc.smartkin.support.TestData.PARENT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AssignmentMapperTest {

    private final AssignmentMapper mapper = new AssignmentMapperImpl();

    @Test
    void mapsAnAssignmentAndLetsTheCallerAttachTheProgress() {
        var parent = new ParentEntity();
        parent.setId(PARENT_ID);
        var child = new ChildEntity();
        child.setId(CHILD_ID);
        var activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        activity.setTitle("Passwords");
        activity.setLessonId(LESSON_ID);
        var assignment = new AssignedActivityEntity();
        var id = UUID.randomUUID();
        assignment.setId(id);
        assignment.setChild(child);
        assignment.setActivity(activity);
        assignment.setAssignedBy(parent);
        assignment.setAssignedAt(Instant.parse("2026-10-21T12:00:00Z"));
        assignment.setDueDate(Instant.parse("2026-11-01T00:00:00Z"));
        var progress = new ActivityProgressEntity();
        progress.setAssignment(assignment);
        progress.setCompletionPercentage(55);
        progress.setScore(70);
        progress.setParentFeedback("Well done");

        var response = mapper.toResponse(assignment);
        var withProgress = response.withProgress(mapper.toResponse(progress));

        assertEquals(id, response.assignmentId());
        assertEquals(CHILD_ID, response.childId());
        assertEquals(ACTIVITY_ID, response.activityId());
        assertEquals("Passwords", response.activityTitle());
        assertEquals(LESSON_ID, response.lessonId());
        assertEquals(PARENT_ID, response.assignedBy());
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), response.dueDate());
        assertNull(response.progress());
        assertEquals(55, withProgress.progress().completionPercentage());
        assertEquals(id, withProgress.progress().assignmentId());
        assertEquals(70, withProgress.progress().score());
        assertEquals("Well done", withProgress.progress().parentFeedback());
        assertNull(withProgress.withProgress(null).progress());
    }
}
