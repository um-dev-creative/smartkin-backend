package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.QuizAttemptService;
import com.umdc.smartkin.api.v1.to.QuizAttemptRequest;
import com.umdc.smartkin.api.v1.to.QuizAttemptResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/// REST controller for quiz attempts (shares the `/api/v1/assignments` base with the assignment controllers).
@RestController
@RequestMapping("/api/v1/assignments")
public class QuizController implements QuizApi {

    private final QuizAttemptService quizAttemptService;

    /// Creates the controller.
    ///
    /// @param quizAttemptService the service that backs the operations
    public QuizController(QuizAttemptService quizAttemptService) {
        this.quizAttemptService = quizAttemptService;
    }

    @Override
    public QuizAttemptService getService() {
        return quizAttemptService;
    }

    @Override
    public ResponseEntity<List<QuizAttemptResponse>> listQuizAttempts(String token, UUID assignmentId) {
        return quizAttemptService.list(token, assignmentId);
    }

    @Override
    public ResponseEntity<QuizAttemptResponse> submitQuizAttempt(String token, UUID assignmentId, QuizAttemptRequest request) {
        return quizAttemptService.submit(token, assignmentId, request);
    }
}
