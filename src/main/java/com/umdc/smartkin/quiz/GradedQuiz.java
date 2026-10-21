package com.umdc.smartkin.quiz;

import com.umdc.smartkin.mongo.document.QuizAttemptDocument;

import java.util.List;

/// Result of grading one attempt.
///
/// @param score      points earned
/// @param maxScore   points of the whole quiz
/// @param percentage `score` over `maxScore`, rounded down, 0 for a quiz worth no points
/// @param passed     `percentage` reaches the passing percentage of the quiz
/// @param answers    one entry per question of the quiz, in the quiz order; a question left unanswered has no selection and earns nothing
public record GradedQuiz(int score, int maxScore, int percentage, boolean passed, List<QuizAttemptDocument.Answer> answers) {
}
