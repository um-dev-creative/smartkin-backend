package com.umdc.smartkin.quiz;

import com.umdc.smartkin.api.v1.to.QuizAnswerRequest;
import com.umdc.smartkin.mongo.document.LessonDocument;
import com.umdc.smartkin.mongo.document.QuizAttemptDocument;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Grades a quiz on the server. A question is correct only when the selected options are **exactly** the correct ones (no partial credit)
/// and then earns all its points. The percentage is `score * 100 / maxScore` rounded down and the quiz is passed when it reaches the
/// quiz's `passingPercentage`. A question with no correct option in the lesson never earns points.
@Component
public class QuizGrader {

    private static final int HUNDRED = 100;

    /// Creates the grader; it keeps no state.
    public QuizGrader() {
        // Stateless
    }

    /// Grades the submitted answers.
    ///
    /// @param quiz      the quiz of the lesson version being graded
    /// @param submitted the answers of the child
    /// @param now       the instant recorded as the answer time
    /// @return the graded attempt data
    /// @throws InvalidQuizAnswersException when an answer names an unknown question or option, or a question is answered twice
    public GradedQuiz grade(LessonDocument.Quiz quiz, List<QuizAnswerRequest> submitted, Instant now) {
        Map<String, LessonDocument.Question> questions = quiz.questions().stream()
                .collect(Collectors.toMap(LessonDocument.Question::id, Function.identity()));
        Map<String, Set<String>> selections = new ConcurrentHashMap<>();
        for (var answer : submitted) {
            var question = questions.get(answer.questionId());
            if (question == null) {
                throw new InvalidQuizAnswersException("Unknown question " + answer.questionId());
            }
            if (selections.containsKey(question.id())) {
                throw new InvalidQuizAnswersException("Question " + question.id() + " was answered twice");
            }
            var optionIds = question.options().stream().map(LessonDocument.Option::id).collect(Collectors.toSet());
            var selected = new HashSet<>(answer.selectedOptionIds());
            if (!optionIds.containsAll(selected)) {
                throw new InvalidQuizAnswersException("Unknown option for question " + question.id());
            }
            selections.put(question.id(), selected);
        }
        int score = 0;
        int maxScore = 0;
        var graded = new ArrayList<QuizAttemptDocument.Answer>();
        for (var question : quiz.questions()) {
            var selected = selections.getOrDefault(question.id(), Set.of());
            var correctIds = question.options().stream().filter(LessonDocument.Option::correct).map(LessonDocument.Option::id)
                    .collect(Collectors.toSet());
            boolean correct = !correctIds.isEmpty() && correctIds.equals(selected);
            int points = correct ? question.points() : 0;
            score += points;
            maxScore += question.points();
            graded.add(new QuizAttemptDocument.Answer(question.id(), question.options().stream().map(LessonDocument.Option::id)
                    .filter(selected::contains).toList(), correct, points, now));
        }
        int percentage = maxScore == 0 ? 0 : (int) ((long) score * HUNDRED / maxScore);
        return new GradedQuiz(score, maxScore, percentage, percentage >= quiz.passingPercentage(), List.copyOf(graded));
    }
}
