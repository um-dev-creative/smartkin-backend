package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import com.umdc.smartkin.jpa.repository.ActivityProgressRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/// Writes the result of a graded quiz into the progress of the assignment. The caller already holds the lock on the assignment.
@Component
public class ProgressRecorder {

    private static final int COMPLETE = 100;

    private final ActivityProgressRepository progressRepository;

    /// Creates the recorder.
    ///
    /// @param progressRepository the progress repository
    public ProgressRecorder(ActivityProgressRepository progressRepository) {
        this.progressRepository = progressRepository;
    }

    /// Records a graded attempt: the progress keeps the **best** percentage as its score, and passing the quiz completes the assignment
    /// (100% and `completionTime`, set once). A failed attempt never lowers the completion percentage the child already reached.
    ///
    /// @param assignment the assignment the attempt belongs to
    /// @param percentage percentage of the attempt
    /// @param passed     whether the quiz was passed
    /// @param now        the current instant
    public void record(AssignedActivityEntity assignment, int percentage, boolean passed, Instant now) {
        var progress = progressRepository.findByAssignmentId(assignment.getId()).orElseGet(() -> {
            var created = new ActivityProgressEntity();
            created.setAssignment(assignment);
            created.setCompletionPercentage(0);
            created.setStartTime(now);
            return created;
        });
        progress.setLastAccessed(now);
        if (progress.getScore() == null || percentage > progress.getScore()) {
            progress.setScore(percentage);
        }
        if (passed) {
            progress.setCompletionPercentage(COMPLETE);
            if (progress.getCompletionTime() == null) {
                progress.setCompletionTime(now);
            }
        }
        progressRepository.saveAndFlush(progress);
    }
}
